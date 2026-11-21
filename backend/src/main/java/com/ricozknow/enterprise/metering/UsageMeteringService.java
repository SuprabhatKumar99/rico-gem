package com.ricozknow.enterprise.metering;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UsageMeteringService {

    private final UsageMeterRepository meters;
    private final AiCostEventRepository costs;

    /**
     * Increment a usage meter atomically.
     *
     * IMPORTANT:
     * Do not use find -> create -> save here.
     * Multiple concurrent API requests can otherwise create
     * the same (tenant, metric, period) row simultaneously.
     */
    @Transactional
    public void increment(
            String metric,
            long amount
    ) {

        UUID tenantId = TenantContext.get();

        if (tenantId == null) {
            return;
        }

        if (metric == null || metric.isBlank()) {
            return;
        }

        if (amount <= 0) {
            return;
        }

        LocalDate periodStart =
                LocalDate.now(ZoneOffset.UTC)
                        .withDayOfMonth(1);

        meters.incrementAtomically(
                tenantId,
                metric,
                periodStart,
                BigDecimal.valueOf(amount)
        );
    }

    @Transactional
    public AiCostEvent recordAiCost(
            String provider,
            String model,
            String operation,
            long in,
            long out,
            long emb,
            long micros,
            String requestId
    ) {

        AiCostEvent e = new AiCostEvent();

        e.setTenantId(TenantContext.get());
        e.setProvider(provider);
        e.setModel(model);
        e.setOperation(operation);
        e.setInputTokens(in);
        e.setOutputTokens(out);
        e.setEmbeddingTokens(emb);
        e.setCostMicros(micros);
        e.setRequestId(requestId);

        return costs.save(e);
    }

    @Transactional(readOnly = true)
    public List<UsageMeter> current() {

        return meters.findByTenantIdAndPeriodStart(
                TenantContext.get(),
                LocalDate.now(ZoneOffset.UTC)
                        .withDayOfMonth(1)
        );
    }

    @Transactional(readOnly = true)
    public List<AiCostEvent> costs(
            Instant from,
            Instant to
    ) {

        return costs.findByTenantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                TenantContext.get(),
                from,
                to
        );
    }
}