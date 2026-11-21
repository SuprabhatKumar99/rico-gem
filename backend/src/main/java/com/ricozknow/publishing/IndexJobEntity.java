package com.ricozknow.publishing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "index_jobs", indexes = {
        @Index(name = "idx_index_jobs_claim", columnList = "status,next_attempt_at,created_at"),
        @Index(name = "idx_index_jobs_tenant_article", columnList = "tenant_id,article_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class IndexJobEntity {
    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IndexJobStatus status = IndexJobStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
}
