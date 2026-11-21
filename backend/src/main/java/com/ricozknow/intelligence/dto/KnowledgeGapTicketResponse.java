package com.ricozknow.intelligence.dto;
import java.util.List; import java.util.UUID;
public record KnowledgeGapTicketResponse(UUID id, String queryText, int volume, int zeroResultCount, int unsuccessfulCount, int gapScore, String status, String suggestedTitle, List<String> suggestedOutline, List<String> suggestedKeywords, UUID articleId) {}
