package com.ricozknow.rag.dto;
import jakarta.validation.constraints.NotBlank;
public record RagConversationRenameRequest(@NotBlank String title) {}
