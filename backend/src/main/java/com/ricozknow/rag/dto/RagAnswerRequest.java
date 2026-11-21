package com.ricozknow.rag.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.UUID;
public record RagAnswerRequest(@NotBlank String query, UUID conversationId, Integer topK, UUID categoryId, Set<String> visibilities, List<UUID> articleIds, String language) {}
