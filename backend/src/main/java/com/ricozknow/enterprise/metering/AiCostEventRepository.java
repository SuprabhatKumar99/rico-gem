package com.ricozknow.enterprise.metering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface AiCostEventRepository extends JpaRepository<AiCostEvent, UUID> {
    long countByTenantIdAndCreatedAtBetween(UUID tenantId, Instant from, Instant to);
    List<AiCostEvent> findByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(UUID tenantId, Instant from, Instant to);
    @Query("select coalesce(sum(e.costMicros),0) from AiCostEvent e where e.tenantId = :tenantId and e.createdAt >= :from")
    long sumCostMicros(@Param("tenantId") UUID tenantId, @Param("from") Instant from);
}
