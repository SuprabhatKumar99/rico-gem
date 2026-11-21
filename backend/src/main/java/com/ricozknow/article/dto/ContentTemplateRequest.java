package com.ricozknow.article.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record ContentTemplateRequest(@NotBlank @Size(max=120) String key, @NotBlank @Size(max=180) String name, String description, @NotNull JsonNode content) {}
