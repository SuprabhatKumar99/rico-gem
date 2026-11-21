package com.ricozknow.enterprise.session;

import com.ricozknow.auth.RefreshToken;
import com.ricozknow.auth.RefreshTokenRepository;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final RefreshTokenRepository repo;

    /** The database, rather than a Java stream over every tenant, scopes the listing. */
    @Transactional(readOnly = true)
    public List<SessionView> list(UUID userId) {
        return repo.findByTenantIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
                        TenantContext.get(), userId, Instant.now())
                .stream().map(t -> new SessionView(t.getId(), t.getDeviceId(),
                        t.getDeviceName(), t.getUserAgent(), t.getIpAddress(),
                        t.getLastUsedAt(), t.getExpiresAt())).toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID id) {
        RefreshToken token = repo.findByTenantIdAndUserIdAndId(TenantContext.get(), userId, id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        token.setRevokedAt(Instant.now());
    }

    public record SessionView(UUID id, String deviceId, String deviceName,
                              String userAgent, String ipAddress,
                              Instant lastUsedAt, Instant expiresAt) {}
}
