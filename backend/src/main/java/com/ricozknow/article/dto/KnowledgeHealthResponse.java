package com.ricozknow.article.dto;
import java.util.List;
import java.util.UUID;
public record KnowledgeHealthResponse(int total, long published, long dueForReview, long unverified, long stale, List<UUID> dueArticleIds) {}
