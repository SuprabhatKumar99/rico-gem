package com.ricozknow.intelligence.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
public record AiImproveRequest(@NotNull JsonNode content, String instruction) {}
