package com.ricozknow.portal.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleVersion;

import java.util.List;
import java.util.UUID;

public record PortalArticleDetail(
        UUID id,
        String slug,
        String title,
        UUID categoryId,
        JsonNode content,
        int versionNumber,
        boolean pinned,
        boolean featured,
        List<String> tags,
        List<UUID> relatedArticleIds,
        List<Translation> translations
) {

    public record Translation(
            UUID articleId,
            String language,
            String slug,
            String title
    ) {}

    public static PortalArticleDetail from(
            Article article,
            ArticleVersion version) {

        return new PortalArticleDetail(
                article.getId(),
                article.getSlug(),
                article.getTitle(),
                article.getCategory() != null
                        ? article.getCategory().getId()
                        : null,
                version.getContent(),
                version.getVersionNumber(),
                article.isPinned(), article.isFeatured(), List.of(), List.of(), List.of()
        );
    }
}