package com.ricozknow.enterprise.privacy;

import com.ricozknow.audit.AuditLogRepository;
import com.ricozknow.enterprise.settings.TenantSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Opt-in, documented compliance purge. A transaction-local marker allows only
 * retention deletes through the immutable audit row trigger. Run under a narrowly
 * privileged DB principal and ship retention receipts off database periodically.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ricozknow.audit.retention-enabled", havingValue = "true")
public class RetentionScheduler {
    private final TenantSettingsRepository settings;
    private final AuditLogRepository audit;
    private final JdbcTemplate jdbc;

    @Scheduled(cron = "${RICOZKNOW_RETENTION_CRON:0 30 2 * * *}")
    @Transactional
    public void purge() {
        settings.findAll().forEach(s -> {
            // Never allow the tenancy setting to erase less than a year's audit evidence.
            int days = Math.max(365, s.getRetentionDays());
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            // PostgreSQL SET LOCAL affects only the current transaction/connection.
            jdbc.execute("SET LOCAL ricoz.audit_retention_mode = 'purge'");
            // JPA derived delete loads rows first; the DB trigger guards each DELETE.
            int count = audit.deleteByTenantIdAndCreatedAtBefore(s.getTenantId(), cutoff);
            jdbc.update("INSERT INTO audit_retention_receipts(tenant_id, purged_before, deleted_count) VALUES (?, ?, ?)",
                    s.getTenantId(), java.sql.Timestamp.from(cutoff), count);
        });
    }
}
