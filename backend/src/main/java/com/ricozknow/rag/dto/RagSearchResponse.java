package com.ricozknow.rag.dto;

import java.util.List;
import java.util.UUID;

public record RagSearchResponse(String query, List<RagSearchResult> results, RetrievalMetadata retrieval) {
 public record RagSearchResult(String chunkId, UUID articleId, UUID versionId, String slug, String title, String content, double score, double semanticScore, double lexicalScore, int rank, String categoryId, String categoryName, String visibility, String publishedAt, String sourceId, String sourceType, String sourceUrl, String language) {}
 public record RetrievalMetadata(String mode, int candidateCount, int returnedCount, long latencyMs) {}
}
