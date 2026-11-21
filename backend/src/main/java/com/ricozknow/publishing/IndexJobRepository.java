package com.ricozknow.publishing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IndexJobRepository extends JpaRepository<IndexJobEntity, UUID> {

    @Query(value = """
            SELECT * FROM index_jobs
            WHERE status IN ('PENDING', 'RETRYING')
              AND next_attempt_at <= now()
            ORDER BY created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<IndexJobEntity> findNextClaimable();

    long countByTenantIdAndStatus(UUID tenantId, IndexJobStatus status);

    Optional<IndexJobEntity> findTopByTenantIdAndArticleIdOrderByCreatedAtDesc(UUID tenantId, UUID articleId);

    boolean existsByTenantIdAndArticleIdAndVersionIdAndStatusIn(UUID tenantId, UUID articleId, UUID versionId, List<IndexJobStatus> statuses);

    java.util.List<IndexJobEntity> findTop200ByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Modifying
    @Query(value = "UPDATE index_jobs SET status = 'RETRYING', next_attempt_at = now(), last_error = COALESCE(last_error, 'Recovered stale processing job') WHERE status = 'PROCESSING' AND started_at < now() - (:timeoutSeconds * INTERVAL '1 second')", nativeQuery = true)
    int recoverStaleProcessingJobs(@Param("timeoutSeconds") long timeoutSeconds);
}
