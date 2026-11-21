package com.ricozknow.rag.dto;
import java.util.List;
import java.util.UUID;
public record RagAnswerResponse(String answer, UUID conversationId, List<Citation> citations, long latencyMs, boolean grounded, double confidence, String contractVersion) {
 public record Citation(String citationId, String chunkId, UUID articleId, UUID versionId, String title, String slug, int rank, double score, String content, String sourceId, String sourceType, String sourceUrl, String language, String evidence) {}
}
