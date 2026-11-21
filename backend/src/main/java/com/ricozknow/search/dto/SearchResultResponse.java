package com.ricozknow.search.dto;

import java.util.List;
import java.util.UUID;

public record SearchResultResponse(
        String query,
        String rewrittenQuery,
        List<SearchHit> results,
        long total,
        Facets facets,
        List<String> relatedQuestions
) {
    public record SearchHit(
            UUID articleId,
            String sourceId,
            String sourceType,
            String sourceUrl,
            String slug,
            String title,
            String excerpt,
            UUID categoryId,
            String language,
            String articleType,
            List<String> tags,
            double score
    ) {}

    public record Facets(
            List<FacetValue> categories,
            List<FacetValue> languages,
            List<FacetValue> tags,
            List<FacetValue> articleTypes
    ) {}

    public record FacetValue(String value, long count) {}
}
