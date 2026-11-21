package com.ricozknow.article.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.ArticleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ArticleCreateRequest(
        @NotBlank String title,
        @NotBlank String slug,
        UUID categoryId,
        String language,
        ArticleType articleType,
        @NotNull JsonNode content
) {
    public ArticleCreateRequest(String title, String slug, UUID categoryId, JsonNode content) {
        this(title, slug, categoryId, "en", ArticleType.GENERAL, content);
    }
    public ArticleCreateRequest(String title, String slug, UUID categoryId, String language, JsonNode content) {
        this(title, slug, categoryId, language, ArticleType.GENERAL, content);
    }
}
