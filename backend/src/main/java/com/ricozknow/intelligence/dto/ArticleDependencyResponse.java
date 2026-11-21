package com.ricozknow.intelligence.dto;
import java.util.UUID;
public record ArticleDependencyResponse(UUID id, UUID articleId, UUID dependsOnArticleId, String dependencyType, String evidence, double confidence) {}
