package com.ricozknow.article.dto;
import java.util.List;
import java.util.UUID;
public record ContentQualityResponse(UUID articleId, UUID versionId, int score, boolean readyForVerification, List<String> warnings) {}
