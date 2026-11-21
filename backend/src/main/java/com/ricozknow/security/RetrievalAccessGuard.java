package com.ricozknow.security;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.article.Visibility;
import com.ricozknow.article.VersionStatus;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagChunkDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Final DB-backed gate for index results. Indices are never the permission authority. */
@Component
@RequiredArgsConstructor
public class RetrievalAccessGuard {
    private final ArticleRepository articles;

    /** A machine RAG API key has PUBLIC-only access, even when a caller requests broader scopes. */
    public boolean publicArticle(UUID articleId, UUID versionId) {
        return portalArticle(articleId, versionId, false);
    }

    /** Portal customer/agent can read AUTHENTICATED only after their controller verifies identity. */
    public boolean portalArticle(UUID articleId, UUID versionId, boolean customerOrAgentAuthenticated) {
        if (articleId == null) return false;
        Article article = articles.findByTenantIdAndId(TenantContext.get(), articleId).orElse(null);
        if (article == null || !TenantContext.get().equals(article.getTenantId())
                || article.getStatus() != ArticleStatus.PUBLISHED
                || (article.getVisibility() != Visibility.PUBLIC &&
                    !(customerOrAgentAuthenticated && article.getVisibility() == Visibility.AUTHENTICATED))
                || article.getActiveVersion() == null
                || article.getActiveVersion().getStatus() != VersionStatus.PUBLISHED) return false;
        return versionId == null || article.getActiveVersion().getId().equals(versionId);
    }

    /** Never authorize indexed source documents or PRIVATE/AUTHENTICATED/GROUP by metadata alone. */
    public boolean publicChunk(RagChunkDocument chunk) {
        if (chunk == null || chunk.sourceId() != null || !"PUBLIC".equals(chunk.visibility())
                || chunk.articleId() == null || chunk.versionId() == null
                || !TenantContext.get().toString().equals(chunk.tenantId())) return false;
        try {
            return publicArticle(UUID.fromString(chunk.articleId()), UUID.fromString(chunk.versionId()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
