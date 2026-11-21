package com.ricozknow.publishing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Creates durable PostgreSQL indexing jobs. Redis is intentionally not the
 * source of truth anymore: a database-backed job survives Redis loss and
 * application restarts. The worker polls the durable table.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IndexJobPublisher {

    private final IndexJobService indexJobService;

    public void enqueueIndexJob(UUID tenantId, UUID articleId, UUID versionId) {
        indexJobService.create(tenantId, articleId, versionId);
    }

    public void enqueueRemovalJob(UUID tenantId, UUID articleId) {
        indexJobService.create(tenantId, articleId, null);
    }

    /** Kept as a stable payload shape for integrations/tests that referenced the old queue contract. */
    public record IndexJob(UUID tenantId, UUID articleId, UUID versionId, Instant enqueuedAt) {}
}
