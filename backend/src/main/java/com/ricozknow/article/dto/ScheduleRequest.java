package com.ricozknow.article.dto;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
public record ScheduleRequest(@NotNull Instant at) {}
