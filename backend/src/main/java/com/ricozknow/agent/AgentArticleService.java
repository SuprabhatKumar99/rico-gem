// package com.ricozknow.agent;

// import com.ricozknow.agent.dto.AgentArticleResponse;
// import com.ricozknow.article.Article;
// import com.ricozknow.article.ArticleRepository;
// import com.ricozknow.article.ArticleStatus;
// import com.ricozknow.article.Visibility;
// import com.ricozknow.audit.AuditService;
// import com.ricozknow.common.TenantContext;
// import lombok.RequiredArgsConstructor;
// import org.springframework.stereotype.Service;
// import org.springframework.transaction.annotation.Transactional;

// import java.util.Set;
// import java.util.UUID;

// /**
//  * The Agent API is the one place that closes the "authenticated article
//  * visibility" gap documented since Phase 3: callers here have real identity
//  * (a staff account or an AGENT_VIEWER service account), so both PUBLIC and
//  * AUTHENTICATED visibility content is in scope. PRIVATE never is — that tier
//  * stays internal-knowledge-studio-only regardless of caller.
//  */
// @Service
// @RequiredArgsConstructor
// public class AgentArticleService {

//     private static final Set<Visibility> AGENT_VISIBLE = Set.of(Visibility.PUBLIC, Visibility.AUTHENTICATED);

//     private final ArticleRepository articleRepository;
//     private final AuditService auditService;

//     @Transactional(readOnly = true)
//     public AgentArticleResponse getById(UUID articleId) {
//         Article article = articleRepository.findByTenantIdAndId(TenantContext.get(), articleId)
//                 .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
//                 .filter(a -> AGENT_VISIBLE.contains(a.getVisibility()))
//                 .orElseThrow(() -> new AgentArticleNotFoundException(articleId));

//         auditService.record("Article", articleId, "AGENT_ARTICLE_VIEWED", null, null);
//         return AgentArticleResponse.from(article, article.getActiveVersion());
//     }
// }


package com.ricozknow.agent;

import com.ricozknow.agent.dto.AgentArticleResponse;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.article.Visibility;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The Agent API is the one place that closes the "authenticated article
 * visibility" gap documented since Phase 3: callers here have real identity
 * (a staff account or an AGENT_VIEWER service account), so both PUBLIC and
 * AUTHENTICATED visibility content is in scope. PRIVATE never is — that tier
 * stays internal-knowledge-studio-only regardless of caller.
 */
@Service
@RequiredArgsConstructor
public class AgentArticleService {

    private static final Set<Visibility> AGENT_VISIBLE = Set.of(Visibility.PUBLIC, Visibility.AUTHENTICATED);
    private static final int MAX_BATCH_SIZE = 50;

    private final ArticleRepository articleRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AgentArticleResponse getById(UUID articleId) {
        Article article = articleRepository.findByTenantIdAndId(TenantContext.get(), articleId)
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .filter(a -> AGENT_VISIBLE.contains(a.getVisibility()))
                .orElseThrow(() -> new AgentArticleNotFoundException(articleId));

        auditService.record("Article", articleId, "AGENT_ARTICLE_VIEWED", null, null);
        return AgentArticleResponse.from(article, article.getActiveVersion());
    }

    /**
     * Batch fetch, e.g. so a CRM can hydrate its cache of frequently-linked
     * articles in one round trip instead of N. Silently omits any id that's
     * missing, unpublished, or not agent-visible rather than failing the
     * whole batch for one bad id — a CRM's stored article-id list can go
     * stale (an article gets archived) without that being the caller's fault
     * or something it needs to handle as an error per id.
     */
    @Transactional(readOnly = true)
    public List<AgentArticleResponse> getByIds(List<UUID> articleIds) {
        if (articleIds.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Cannot request more than " + MAX_BATCH_SIZE + " articles at once");
        }
        UUID tenantId = TenantContext.get();
        List<AgentArticleResponse> results = new java.util.ArrayList<>();
        for (UUID id : articleIds) {
            articleRepository.findByTenantIdAndId(tenantId, id)
                    .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                    .filter(a -> AGENT_VISIBLE.contains(a.getVisibility()))
                    .ifPresent(a -> results.add(AgentArticleResponse.from(a, a.getActiveVersion())));
        }
        if (!results.isEmpty()) {
            auditService.record("Article", null, "AGENT_ARTICLES_BATCH_VIEWED", null, results.size());
        }
        return results;
    }
}