package com.ricozknow.health;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.common.TenantContext;
import com.ricozknow.search.IndexStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Read-only, tenant-scoped knowledge health report; no external AI provider or schema changes required. */
@RestController
@RequestMapping("/api/v1/knowledge-health")
@RequiredArgsConstructor
public class KnowledgeHealthController {
    private final ArticleRepository articles;

    public record Issue(UUID articleId, String title, String code, String severity, String detail) {}
    public record Report(int total, long published, long drafts, long inReview,
                         long searchFailures, long ragFailures, int issueCount,
                         Instant generatedAt, List<Issue> issues) {}

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public Report report() {
        List<Article> all = articles.findAllByTenantId(TenantContext.get());
        List<Issue> issues = new ArrayList<>();
        Instant now = Instant.now();
        long published = 0, drafts = 0, inReview = 0, searchFailures = 0, ragFailures = 0;
        for (Article a : all) {
            if (a.getStatus() == ArticleStatus.PUBLISHED) published++;
            if (a.getStatus() == ArticleStatus.DRAFT) drafts++;
            if (a.getStatus() == ArticleStatus.IN_REVIEW) inReview++;
            if (a.getSearchIndexStatus() == IndexStatus.FAILED) {
                searchFailures++;
                issues.add(new Issue(a.getId(), a.getTitle(), "SEARCH_INDEX_FAILED", "HIGH", "Keyword search indexing failed"));
            }
            if (a.getRagIndexStatus() == IndexStatus.FAILED) {
                ragFailures++;
                issues.add(new Issue(a.getId(), a.getTitle(), "RAG_INDEX_FAILED", "HIGH", "AI retrieval indexing failed"));
            }
            if (a.getStatus() == ArticleStatus.PUBLISHED && a.getCategory() == null)
                issues.add(new Issue(a.getId(), a.getTitle(), "UNCATEGORIZED", "MEDIUM", "Published article has no category"));
            if (a.getStatus() == ArticleStatus.PUBLISHED && a.getActiveVersion() == null)
                issues.add(new Issue(a.getId(), a.getTitle(), "NO_ACTIVE_VERSION", "HIGH", "Published article has no active version"));
            if (a.getScheduledPublishAt() != null && a.getScheduledPublishAt().isBefore(now)
                    && a.getStatus() != ArticleStatus.PUBLISHED)
                issues.add(new Issue(a.getId(), a.getTitle(), "PUBLISH_OVERDUE", "HIGH", "Scheduled publication time has passed"));
            if (a.getScheduledUnpublishAt() != null && a.getScheduledUnpublishAt().isBefore(now)
                    && a.getStatus() == ArticleStatus.PUBLISHED)
                issues.add(new Issue(a.getId(), a.getTitle(), "UNPUBLISH_OVERDUE", "HIGH", "Scheduled unpublication time has passed"));
        }
        issues.sort(Comparator.comparing((Issue i) -> !"HIGH".equals(i.severity()))
                .thenComparing(Issue::title, String.CASE_INSENSITIVE_ORDER));
        return new Report(all.size(), published, drafts, inReview, searchFailures, ragFailures,
                issues.size(), now, List.copyOf(issues));
    }
}
