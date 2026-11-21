package com.ricozknow.publishing;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexJobService {
    private final IndexJobRepository repository;

    @Transactional
    public IndexJobEntity create(UUID tenantId, UUID articleId, UUID versionId) {
        IndexJobEntity job = new IndexJobEntity();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenantId);
        job.setArticleId(articleId);
        job.setVersionId(versionId);
        job.setStatus(IndexJobStatus.PENDING);
        job.setAttemptCount(0);
        job.setMaxAttempts(5);
        job.setCreatedAt(Instant.now());
        job.setNextAttemptAt(Instant.now());
        return repository.save(job);
    }

    @Transactional
    public IndexJobEntity claimNext() {
        var job = repository.findNextClaimable().orElse(null);
        if (job == null) return null;
        job.setStatus(IndexJobStatus.PROCESSING);
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setStartedAt(Instant.now());
        return repository.save(job);
    }

    @Transactional
    public void complete(UUID jobId) {
        repository.findById(jobId).ifPresent(job -> {
            job.setStatus(IndexJobStatus.COMPLETED);
            job.setCompletedAt(Instant.now());
            job.setLastError(null);
            repository.save(job);
        });
    }

    @Transactional
    public void fail(UUID jobId, Throwable error) {
        repository.findById(jobId).ifPresent(job -> {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            job.setLastError(message.length() > 4000 ? message.substring(0, 4000) : message);
            if (job.getAttemptCount() >= job.getMaxAttempts()) {
                job.setStatus(IndexJobStatus.FAILED);
                job.setCompletedAt(Instant.now());
            } else {
                job.setStatus(IndexJobStatus.RETRYING);
                long seconds = Math.min(300, 5L * (1L << Math.min(job.getAttemptCount() - 1, 6)));
                job.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(seconds)));
            }
            repository.save(job);
        });
    }

    @Transactional
    public int recoverStaleProcessingJobs(long timeoutSeconds) {
        return repository.recoverStaleProcessingJobs(timeoutSeconds);
    }

    @Transactional(readOnly = true)
    public java.util.List<IndexJobEntity> recentForCurrentTenant() {
        return repository.findTop200ByTenantIdOrderByCreatedAtDesc(TenantContext.get());
    }

    public long pendingForCurrentTenant() {
        UUID tenantId = TenantContext.get();
        return repository.countByTenantIdAndStatus(tenantId, IndexJobStatus.PENDING)
                + repository.countByTenantIdAndStatus(tenantId, IndexJobStatus.RETRYING);
    }
}
