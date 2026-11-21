package com.ricozknow.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, java.util.UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AuditLog> {
    org.springframework.data.domain.Page<AuditLog> findByTenantId(java.util.UUID tenantId, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from AuditLog a where a.tenantId = :tenantId and a.createdAt < :before")
    int deleteByTenantIdAndCreatedAtBefore(@org.springframework.data.repository.query.Param("tenantId") java.util.UUID tenantId,
                                          @org.springframework.data.repository.query.Param("before") java.time.Instant before);
}
