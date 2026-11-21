package com.ricozknow.article.dto;
import java.time.Instant;
import java.util.UUID;
public record GovernanceResponse(UUID articleId, UUID ownerId, int reviewIntervalDays, Instant nextReviewAt, Instant verifiedAt, UUID verifiedBy, boolean reviewDue) {}
