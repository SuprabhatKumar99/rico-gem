package com.ricozknow.intelligence.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
public record AiGenerateRequest(@NotBlank String topic, String sourceMaterial, UUID categoryId, String audience, String language) {}
