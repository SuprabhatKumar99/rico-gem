package com.ricozknow.enterprise.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {
    @Modifying
    @Query("delete from MfaRecoveryCode c where c.tenantId = :tenantId and c.userId = :userId")
    int deleteForUser(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /** Atomic delete makes recovery-code use single-shot even under concurrent logins. */
    @Modifying
    @Query("delete from MfaRecoveryCode c where c.tenantId = :tenantId and c.userId = :userId and c.codeHash = :hash")
    int consume(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("hash") String hash);
}
