package com.ricozknow.portal.dto;

import com.ricozknow.article.Article;

import java.util.UUID;

public record PortalArticleSummary(
        UUID id,
        String slug,
        String title,
        UUID categoryId,
        boolean pinned,
        boolean featured
) {
    public static PortalArticleSummary from(Article article) {
        return new PortalArticleSummary(
                article.getId(), article.getSlug(), article.getTitle(),
                article.getCategory() != null ? article.getCategory().getId() : null, article.isPinned(), article.isFeatured());
    }
}
