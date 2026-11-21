package com.ricozknow.article.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record GovernanceRequest(UUID ownerId, @Min(1) @Max(3650) Integer reviewIntervalDays) {}
