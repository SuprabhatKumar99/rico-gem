package com.ricozknow.search;

import com.ricozknow.article.Article;

import java.time.Instant;
import java.util.UUID;

public record IndexStatusResponse(
        UUID articleId,
        UUID activeVersionId,
        IndexStatus searchStatus,
        IndexStatus ragStatus,
        Instant searchIndexedAt,
        Instant ragIndexedAt,
        String searchError,
        String ragError
) {
    public static IndexStatusResponse from(Article article) {
        return new IndexStatusResponse(article.getId(),
                article.getActiveVersion() != null ? article.getActiveVersion().getId() : null,
                article.getSearchIndexStatus(), article.getRagIndexStatus(),
                article.getSearchIndexedAt(), article.getRagIndexedAt(),
                article.getSearchIndexError(), article.getRagIndexError());
    }
}
