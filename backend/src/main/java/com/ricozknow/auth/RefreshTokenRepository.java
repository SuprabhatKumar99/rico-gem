package com.ricozknow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    /**
     * The refresh service invokes this inside a write transaction. Locking the
     * row makes validation + revocation atomic against a second concurrent
     * refresh request using the same token (PostgreSQL SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByTenantIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            UUID tenantId, UUID userId, Instant now);

    Optional<RefreshToken> findByTenantIdAndUserIdAndId(UUID tenantId, UUID userId, UUID id);
}
