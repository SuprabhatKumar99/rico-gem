package com.ricozknow.enterprise.scim;

import com.ricozknow.audit.AuditService;
import com.ricozknow.auth.RefreshTokenService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.RoleName;
import com.ricozknow.role.RoleRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Tenant-isolated, deliberately least-privilege SCIM 2.0 user provisioning. */
@Service
@RequiredArgsConstructor
public class ScimService {
    private final com.ricozknow.billing.BillingService billing;
    private final ScimTokenRepository tokens;
    private final UserRepository users;
    private final RoleRepository roles;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;

    public record TokenResult(UUID id, String name, String token, String prefix) {}
    public record Change(String path, Object value) {}

    @Transactional
    public TokenResult createToken(String name) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = "scim_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ScimToken token = new ScimToken();
        token.setTenantId(TenantContext.get());
        token.setName(name == null || name.isBlank() ? "SCIM" : name.trim());
        token.setTokenHash(hash(raw));
        token.setTokenPrefix(raw.substring(0, 12));
        tokens.save(token);
        audit.record("ScimToken", token.getId(), "SCIM_TOKEN_CREATED", null, token.getTokenPrefix());
        return new TokenResult(token.getId(), token.getName(), raw, token.getTokenPrefix());
    }

    @Transactional(readOnly = true)
    public java.util.List<ScimToken> listTokens() {
        return tokens.findByTenantIdOrderByCreatedAtDesc(TenantContext.get());
    }

    @Transactional
    public void revokeToken(UUID id) {
        tokens.findById(id).filter(t -> TenantContext.get().equals(t.getTenantId()))
                .ifPresent(t -> {
                    t.setActive(false);
                    audit.record("ScimToken", id, "SCIM_TOKEN_REVOKED", null, t.getTokenPrefix());
                });
    }

    @Transactional
    public ScimToken authenticate(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 256) return null;
        return tokens.findByTokenHashAndActive(hash(raw), true)
                .map(t -> {
                    t.setLastUsedAt(Instant.now());
                    return t;
                }).orElse(null);
    }

    /** Exact userName filter for SCIM client reconciliation, always tenant-scoped. */
    @Transactional(readOnly = true)
    public java.util.Optional<User> findByUserName(String email) {
        if (email == null || email.isBlank()) return java.util.Optional.empty();
        return users.findByTenantIdAndEmailIgnoreCase(TenantContext.get(), email.trim());
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id)
                .filter(u -> TenantContext.get().equals(u.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SCIM user not found"));
    }

    @Transactional(readOnly = true)
    public Page<User> list(int startIndex, int count) {
        if (startIndex < 1 || count < 0 || count > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startIndex must be >= 1 and count 0..100");
        }
        return users.findByTenantId(TenantContext.get(), new OffsetPageable(startIndex - 1, Math.max(1, count)));
    }

    @Transactional
    public User create(String email, String displayName, boolean active) {
        String normalized = normalizeEmail(email);
        // An existing account must never be silently re-claimed or have its status
        // overwritten by a provisioner merely because the email address matches.
        if (users.findByTenantIdAndEmailIgnoreCase(TenantContext.get(), normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SCIM userName already exists");
        }
        if(active) billing.checkCapacity(TenantContext.get(),"max_seats");
        User user = new User();
        user.setTenantId(TenantContext.get());
        user.setEmail(normalized);
        user.setName(displayName == null || displayName.isBlank() ? normalized : displayName.trim());
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        user.setEmailVerified(true);
        user.setRoles(new HashSet<>());
        user.getRoles().add(roles.findByTenantIdAndName(TenantContext.get(), RoleName.AGENT_VIEWER.name())
                .orElseThrow(() -> new IllegalStateException("Tenant AGENT_VIEWER role not configured")));
        user.setStatus(active ? User.UserStatus.ACTIVE : User.UserStatus.DISABLED);
        user = users.save(user);
        audit.record("User", user.getId(), "SCIM_USER_CREATED", null, normalized);
        return user;
    }

    @Transactional
    public User replace(UUID id, String email, String displayName, boolean active) {
        User user = get(id); // Strict tenant + resource-ID match.
        updateEmail(user, email);
        user.setName(displayName == null || displayName.isBlank() ? user.getEmail() : displayName.trim());
        applyActive(user, active);
        audit.record("User", id, "SCIM_USER_REPLACED", null, user.getEmail());
        return user;
    }

    @Transactional
    public User patch(UUID id, java.util.List<Change> changes) {
        User user = get(id);
        if (changes == null || changes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCIM Operations required");
        }
        for (Change change : changes) {
            String path = change.path() == null ? "" : change.path().trim().toLowerCase(Locale.ROOT);
            switch (path) {
                case "active" -> {
                    if (!(change.value() instanceof Boolean value))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active must be boolean");
                    applyActive(user, value);
                }
                case "username" -> updateEmail(user, asString(change.value(), "userName"));
                case "displayname" -> user.setName(asString(change.value(), "displayName").trim());
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unsupported SCIM PATCH path: " + path);
            }
        }
        audit.record("User", id, "SCIM_USER_PATCHED", null, user.getEmail());
        return user;
    }

    /** SCIM DELETE is a soft deprovision: preserve audit/history and revoke sessions. */
    @Transactional
    public void deactivate(UUID id) {
        User user = get(id);
        applyActive(user, false);
        audit.record("User", id, "SCIM_USER_DEACTIVATED", null, null);
    }

    private void applyActive(User user, boolean active) {
        if (active && user.getStatus() != User.UserStatus.ACTIVE) billing.checkCapacity(TenantContext.get(),"max_seats");
        if (!active) refreshTokens.revokeAllForUser(user.getId());
        user.setStatus(active ? User.UserStatus.ACTIVE : User.UserStatus.DISABLED);
    }

    private void updateEmail(User user, String email) {
        String normalized = normalizeEmail(email);
        users.findByTenantIdAndEmailIgnoreCase(TenantContext.get(), normalized).ifPresent(other -> {
            if (!other.getId().equals(user.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SCIM userName already exists");
        });
        user.setEmail(normalized);
    }

    private static String asString(Object value, String field) {
        if (!(value instanceof String s) || s.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a non-empty string");
        return s;
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 320 || !email.contains("@"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid userName email required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Exact SCIM offset pagination (not restricted to page-number multiples). */
    private record OffsetPageable(long offset, int size) implements Pageable {
        @Override public int getPageNumber() { return (int) (offset / size); }
        @Override public int getPageSize() { return size; }
        @Override public long getOffset() { return offset; }
        @Override public Sort getSort() { return Sort.by("email").ascending(); }
        @Override public Pageable next() { return new OffsetPageable(offset + size, size); }
        @Override public Pageable previousOrFirst() { return new OffsetPageable(Math.max(0, offset - size), size); }
        @Override public Pageable first() { return new OffsetPageable(0, size); }
        @Override public Pageable withPage(int pageNumber) { return new OffsetPageable((long) pageNumber * size, size); }
        @Override public boolean hasPrevious() { return offset > 0; }
    }
}
