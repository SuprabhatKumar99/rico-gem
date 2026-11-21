package com.ricozknow.ingestion.dto;

import com.ricozknow.ingestion.*;
import java.time.Instant;
import java.util.UUID;

public record KnowledgeSourceResponse(UUID id, KnowledgeSourceType type, String name, String url, String mimeType,
                                      String language, KnowledgeSourceStatus status, Integer syncIntervalMinutes,
                                      Instant nextSyncAt, Instant lastSyncedAt, int discoveredPages, String lastError, KnowledgeProcessingPhase processingPhase, int retryCount, int maxRetries, Instant lastAttemptAt) {
    public static KnowledgeSourceResponse from(KnowledgeSource s) {
        return new KnowledgeSourceResponse(s.getId(), s.getType(), s.getName(), s.getUrl(), s.getMimeType(), s.getLanguage(),
                s.getStatus(), s.getSyncIntervalMinutes(), s.getNextSyncAt(), s.getLastSyncedAt(), s.getDiscoveredPages(), s.getLastError(), s.getProcessingPhase(), s.getRetryCount(), s.getMaxRetries(), s.getLastAttemptAt());
    }
}
