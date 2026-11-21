package com.ricozknow.intelligence.dto;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
public record AiGeneratedArticleResponse(String title, String suggestedSlug, JsonNode content, List<String> keywords, List<String> topics, List<String> outline) {}
