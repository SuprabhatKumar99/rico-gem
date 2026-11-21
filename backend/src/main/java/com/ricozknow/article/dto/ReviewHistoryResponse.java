package com.ricozknow.article.dto;
import java.time.Instant;
import java.util.UUID;
public record ReviewHistoryResponse(UUID id, UUID versionId, UUID reviewerId, String decision, String comment, Instant createdAt) {}
