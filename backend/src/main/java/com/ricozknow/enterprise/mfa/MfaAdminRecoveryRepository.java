package com.ricozknow.enterprise.mfa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import java.util.UUID;

public interface MfaAdminRecoveryRepository extends JpaRepository<MfaAdminRecoveryRequest, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MfaAdminRecoveryRequest> findByTenantIdAndId(UUID tenantId, UUID id);
    Page<MfaAdminRecoveryRequest> findByTenantId(UUID tenantId, Pageable pageable);
}
