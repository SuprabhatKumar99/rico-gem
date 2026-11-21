package com.ricozknow.article.dto;
import jakarta.validation.constraints.NotBlank;
public record BlockCommentRequest(@NotBlank @jakarta.validation.constraints.Size(max=100) String blockId, @NotBlank @jakarta.validation.constraints.Size(max=5000) String comment, java.util.UUID parentId) {}
