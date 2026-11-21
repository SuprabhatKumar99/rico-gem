package com.ricozknow.ingestion;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_sources")
@Getter @Setter @NoArgsConstructor
public class KnowledgeSource extends TenantOwnedEntity {
    @Enumerated(EnumType.STRING) @Column(nullable=false) private KnowledgeSourceType type;
    @Column(nullable=false) private String name;
    @Column(columnDefinition="text") private String url;
    @Column(name="storage_key", columnDefinition="text") private String storageKey;
    @Column(name="mime_type") private String mimeType;
    @Column(nullable=false) private String language = "en";
    @Enumerated(EnumType.STRING) @Column(nullable=false) private KnowledgeSourceStatus status = KnowledgeSourceStatus.PENDING;
    @Column(name="sync_interval_minutes") private Integer syncIntervalMinutes;
    @Column(name="next_sync_at") private Instant nextSyncAt;
    @Column(name="max_pages", nullable=false) private int maxPages = 25;
    @Column(name="last_synced_at") private Instant lastSyncedAt;
    @Column(length=128) private String checksum;
    @Column(name="last_error", columnDefinition="text") private String lastError;
    @Enumerated(EnumType.STRING) @Column(name="processing_phase", nullable=false) private KnowledgeProcessingPhase processingPhase = KnowledgeProcessingPhase.PENDING;
    @Column(name="retry_count", nullable=false) private int retryCount;
    @Column(name="max_retries", nullable=false) private int maxRetries = 3;
    @Column(name="last_attempt_at") private Instant lastAttemptAt;
    @Column(name="discovered_pages", nullable=false) private int discoveredPages;
    @Column(name="created_by", nullable=false, updatable=false) private UUID createdBy;
}
