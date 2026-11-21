package com.ricozknow.article.dto;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.article.Visibility;
import com.ricozknow.search.IndexStatus;

import java.time.Instant;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String slug,
        String title,
        String language,
        ArticleStatus status,
        Visibility visibility,
        UUID categoryId,
        UUID activeVersionId,
        Integer activeVersionNumber,
        IndexStatus searchIndexStatus,
        IndexStatus ragIndexStatus,
        Instant searchIndexedAt,
        Instant ragIndexedAt,
        boolean pinned,
        boolean featured,
        Instant scheduledPublishAt,
        Instant scheduledUnpublishAt
) {

    public static ArticleResponse from(Article article) {

        var activeVersion =
                article.getActiveVersion();

        return new ArticleResponse(
                article.getId(),
                article.getSlug(),
                article.getTitle(),
                article.getLanguage(),

                // Article status
                article.getStatus(),

                // Visibility
                article.getVisibility(),

                article.getCategory() != null
                        ? article.getCategory().getId()
                        : null,

                activeVersion != null
                        ? activeVersion.getId()
                        : null,

                activeVersion != null
                        ? activeVersion.getVersionNumber()
                        : null,

                article.getSearchIndexStatus(),
                article.getRagIndexStatus(),

                article.getSearchIndexedAt(),
                article.getRagIndexedAt(),

                article.isPinned(),
                article.isFeatured(),

                article.getScheduledPublishAt(),
                article.getScheduledUnpublishAt()
        );
    }
}