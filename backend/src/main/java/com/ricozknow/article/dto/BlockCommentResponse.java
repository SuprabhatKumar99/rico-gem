package com.ricozknow.article.dto;
import java.time.Instant;
import java.util.UUID;
public record BlockCommentResponse(UUID id, UUID articleId, UUID versionId, String blockId, UUID authorId, String comment, java.util.UUID parentId, boolean resolved, Instant createdAt, Instant resolvedAt) {}
