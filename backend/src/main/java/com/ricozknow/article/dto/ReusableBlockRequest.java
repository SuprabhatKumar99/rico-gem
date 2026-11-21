package com.ricozknow.article.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
public record ReusableBlockRequest(@NotBlank @Size(max=180) String name, @NotNull JsonNode block) {}
