package com.ricozknow.rag.usage;

import com.ricozknow.common.TenantContext;
import com.ricozknow.common.TooManyRequestsException;
import com.ricozknow.enterprise.metering.UsageMeter;
import com.ricozknow.enterprise.metering.UsageMeterRepository;
import com.ricozknow.enterprise.metering.AiCostEventRepository;
import com.ricozknow.enterprise.settings.TenantSettings;
import com.ricozknow.enterprise.settings.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagUsageLimitService {
    private final UsageMeterRepository usageMeterRepository;
    private final TenantSettingsRepository settingsRepository;
    private final AiCostEventRepository aiCostEventRepository;

    @Transactional
    public void consumeRagQuery() {
        UUID tenant = TenantContext.get();
        TenantSettings settings = settingsRepository.findById(tenant).orElseGet(() -> defaultSettings(tenant));
        LocalDate period = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        UsageMeter meter = usageMeterRepository.findByTenantIdAndMetricAndPeriodStart(tenant, "rag_queries", period)
                .orElseGet(() -> { UsageMeter m = new UsageMeter(); m.setTenantId(tenant); m.setMetric("rag_queries"); m.setPeriodStart(period); return m; });
        long limit = settings.getMaxRagQueriesMonthly();
        if (limit > 0 && meter.getQuantity().compareTo(BigDecimal.valueOf(limit)) >= 0) {
            throw new TooManyRequestsException("Monthly RAG query limit exceeded");
        }
        meter.setQuantity(meter.getQuantity().add(BigDecimal.ONE));
        usageMeterRepository.save(meter);
    }

    @Transactional(readOnly = true)
    public void enforceAiCostLimit(long additionalCostMicros) {
        UUID tenant = TenantContext.get();
        TenantSettings settings = settingsRepository.findById(tenant).orElseGet(() -> defaultSettings(tenant));
        long limitMicros = settings.getMaxAiCostCentsMonthly() * 10_000L;
        if (limitMicros <= 0) return;
        java.time.Instant from = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        long current = aiCostEventRepository.sumCostMicros(tenant, from);
        if (current + additionalCostMicros > limitMicros) throw new TooManyRequestsException("Monthly AI cost limit exceeded");
    }

    @Transactional(readOnly = true)
    public long currentRagQueries() {
        UUID tenant = TenantContext.get();
        return usageMeterRepository.findByTenantIdAndMetricAndPeriodStart(tenant, "rag_queries", LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1))
                .map(m -> m.getQuantity().longValue()).orElse(0L);
    }

    private TenantSettings defaultSettings(UUID tenant) {
        TenantSettings s = new TenantSettings(); s.setTenantId(tenant); return s;
    }
}
