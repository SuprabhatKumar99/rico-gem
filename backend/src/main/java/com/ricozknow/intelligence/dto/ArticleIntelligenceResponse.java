package com.ricozknow.intelligence.dto;
import java.time.Instant; import java.util.List; import java.util.UUID;
public record ArticleIntelligenceResponse(UUID articleId, UUID versionId, List<String> keywords, List<String> topics, double healthScore, List<String> healthReasons, Instant analyzedAt) {}
