package com.ricozknow.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Refresh tokens are high-entropy random strings, not user-chosen secrets —
 * unlike passwords, they don't need Argon2's deliberate slowness (which would
 * just waste CPU on every refresh call). A fast SHA-256 hash of the token is
 * sufficient: the security property that matters here is "the plaintext
 * token is never stored," not "brute-forcing the hash is expensive," since
 * the token itself already has far more entropy than any password.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;

    @Value("${ricozknow.security.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    /** @return the plaintext token — this is the only moment it ever exists outside the client. */
    @Transactional
    public String issue(UUID tenantId, UUID userId) {
        return issue(tenantId, userId, refreshTokenTtlDays);
    }

    @Transactional
    public String issue(UUID tenantId, UUID userId, String deviceId, String deviceName, String userAgent, String ipAddress) {
        return issue(tenantId, userId, refreshTokenTtlDays, deviceId, deviceName, userAgent, ipAddress);
    }

    /** Overload for non-default TTLs — see AuthService's agent-vs-staff TTL selection. */
    @Transactional
    public String issue(UUID tenantId, UUID userId, long ttlDays) { return issue(tenantId, userId, ttlDays, null, null, null, null); }

    @Transactional
    public String issue(UUID tenantId, UUID userId, long ttlDays, String deviceId, String deviceName, String userAgent, String ipAddress) {
        String plaintext = generateToken();
        RefreshToken token = new RefreshToken();
        token.setTenantId(tenantId); token.setUserId(userId); token.setTokenHash(hash(plaintext));
        token.setExpiresAt(Instant.now().plus(ttlDays, ChronoUnit.DAYS)); token.setDeviceId(deviceId); token.setDeviceName(deviceName);
        token.setUserAgent(userAgent); token.setIpAddress(ipAddress); token.setLastUsedAt(Instant.now()); repository.save(token); return plaintext;
    }

    /**
     * Validates the presented token and revokes it (the "rotation" half that
     * happens regardless of TTL class), returning who it belonged to. Callers
     * that need to pick a TTL based on the user (e.g. AuthService choosing
     * agent vs. staff duration) look the user up from the returned userId,
     * then call {@link #issue(UUID, UUID, long)} themselves — split into two
     * steps specifically so the TTL decision can depend on data only available
     * after validating the old token, without the new token's TTL secretly
     * defaulting to the wrong class in between.
     */
    @Transactional
    public ValidatedToken validateAndRevoke(String presentedToken) {
        RefreshToken existing = repository.findByTokenHash(hash(presentedToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));
        existing.setRevokedAt(Instant.now());
        existing.setLastUsedAt(Instant.now());
        repository.save(existing);
        return new ValidatedToken(existing.getTenantId(), existing.getUserId());
    }

    /** Convenience for callers that don't need TTL-class awareness: validate, revoke, and reissue at the default (staff) TTL in one call. */
    @Transactional
    public RotationResult rotate(String presentedToken) {
        ValidatedToken validated = validateAndRevoke(presentedToken);
        String newToken = issue(validated.tenantId(), validated.userId(), refreshTokenTtlDays);
        return new RotationResult(validated.tenantId(), validated.userId(), newToken);
    }

    @Transactional
    public void revoke(String presentedToken) {
        repository.findByTokenHash(hash(presentedToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
        });
    }

    /** Revokes every active refresh token for a user — used on password reset / forced logout. */
    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.findAll().stream()
                .filter(t -> t.getUserId().equals(userId) && t.isActive())
                .forEach(t -> t.setRevokedAt(Instant.now()));
    }

    private String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(token.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record RotationResult(UUID tenantId, UUID userId, String newPlaintextToken) {
    }

    public record ValidatedToken(UUID tenantId, UUID userId) {
    }
}