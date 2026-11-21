package com.ricozknow.enterprise.metering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface UsageMeterRepository
        extends JpaRepository<UsageMeter, UUID> {

    Optional<UsageMeter> findByTenantIdAndMetricAndPeriodStart(
            UUID tenantId,
            String metric,
            LocalDate periodStart
    );

    List<UsageMeter> findByTenantIdAndPeriodStart(
            UUID tenantId,
            LocalDate periodStart
    );

    /**
     * PostgreSQL atomic upsert.
     *
     * If the meter does not exist it is created.
     * If it already exists its quantity is incremented.
     *
     * This is safe when multiple API requests arrive concurrently.
     */
    @Modifying
    @Query(value = """
        INSERT INTO usage_meters (
            id,
            tenant_id,
            metric,
            period_start,
            quantity,
            created_at,
            updated_at
        )
        VALUES (
            gen_random_uuid(),
            :tenantId,
            :metric,
            :periodStart,
            :amount,
            now(),
            now()
        )
        ON CONFLICT (tenant_id, metric, period_start)
        DO UPDATE SET
            quantity = usage_meters.quantity + EXCLUDED.quantity,
            updated_at = now()
        """, nativeQuery = true)
    int incrementAtomically(
            @Param("tenantId") UUID tenantId,
            @Param("metric") String metric,
            @Param("periodStart") LocalDate periodStart,
            @Param("amount") BigDecimal amount
    );
}