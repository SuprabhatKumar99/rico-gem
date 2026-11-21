package com.ricozknow.rag.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record RagSearchRequest(@NotBlank String query, @Min(1) @Max(50) Integer topK, UUID categoryId, Set<String> visibilities, List<UUID> articleIds, String language, Integer candidateK) {
    public RagSearchRequest(String query, Integer topK, UUID categoryId, Set<String> visibilities, List<UUID> articleIds, Integer candidateK) {
        this(query, topK, categoryId, visibilities, articleIds, null, candidateK);
    }
    public int resolvedTopK() { return topK == null ? 5 : topK; }
    public int resolvedCandidateK() { return candidateK == null ? Math.min(50, Math.max(10, resolvedTopK() * 4)) : Math.min(100, Math.max(resolvedTopK(), candidateK)); }
}
