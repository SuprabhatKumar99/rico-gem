package com.ricozknow.portal;

import com.ricozknow.analytics.AnalyticsEventService;
import com.ricozknow.analytics.AnalyticsEventType;
import com.ricozknow.intelligence.FeedbackIntelligenceService;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.article.Visibility;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ricozknow.enterprise.group.GroupService;
import com.ricozknow.portal.dto.PortalArticleDetail;
import com.ricozknow.portal.dto.PortalArticleSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MVP scope note: the spec calls for "Public and authenticated article
 * visibility," but the MVP has no customer identity system — only tenant
 * staff (ADMIN/EDITOR/REVIEWER/AGENT_VIEWER) accounts exist. Until customer
 * accounts/SSO land, this service only ever serves PUBLIC visibility content
 * to the anonymous portal; AUTHENTICATED-visibility articles are reachable
 * through the Agent API (which does have real identity) but not yet through
 * the customer portal itself. Revisit when customer auth is scoped.
 *
 * Search itself now lives entirely in SearchService (Phase 4) — the naive
 * DB-substring search this class used in Phase 3 has been removed, not kept
 * as a fallback, per that code's own comment that it should be replaced.
 */
@Service
@RequiredArgsConstructor
public class PortalArticleService {

    private final ArticleRepository articleRepository;
    private final AnalyticsEventService analyticsEventService;
    private final FeedbackIntelligenceService feedbackIntelligenceService;
    private final GroupService groupService;
    private final com.ricozknow.security.DocumentAccessPolicy accessPolicy;
    private final com.ricozknow.customer.CustomerAuthService customerAuthService;
    private final com.ricozknow.translation.ArticleTranslationLinkRepository translationLinks;
    private final com.ricozknow.article.ArticleTagRepository tagRepository;
    private final com.ricozknow.article.ArticleRelatedRepository relatedRepository;

    @Transactional(readOnly = true)
    public Page<PortalArticleSummary> browse(UUID categoryId, Pageable pageable) { return browse(categoryId, pageable, false); }

    @Transactional(readOnly = true)
    public Page<PortalArticleSummary> browse(UUID categoryId, Pageable pageable, boolean authenticated) {
        UUID tenantId = TenantContext.get();
        java.util.List<Visibility> visibility = authenticated
                ? java.util.List.of(Visibility.PUBLIC, Visibility.AUTHENTICATED)
                : java.util.List.of(Visibility.PUBLIC);
        Page<Article> page = categoryId != null
                ? articleRepository.findByTenantIdAndStatusAndVisibilityInAndCategoryId(tenantId, ArticleStatus.PUBLISHED, visibility, categoryId, pageable)
                : articleRepository.findByTenantIdAndStatusAndVisibilityIn(tenantId, ArticleStatus.PUBLISHED, visibility, pageable);
        return page.map(PortalArticleSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<PortalArticleSummary> browsePinned(UUID categoryId, Pageable pageable, boolean authenticated) {
        UUID tenantId = TenantContext.get();
        java.util.List<Visibility> visibility = authenticated
                ? java.util.List.of(Visibility.PUBLIC, Visibility.AUTHENTICATED)
                : java.util.List.of(Visibility.PUBLIC);
        Page<Article> page = categoryId != null
                ? articleRepository.findByTenantIdAndStatusAndVisibilityInAndCategoryIdAndPinnedTrue(tenantId, ArticleStatus.PUBLISHED, visibility, categoryId, pageable)
                : articleRepository.findByTenantIdAndStatusAndVisibilityInAndPinnedTrue(tenantId, ArticleStatus.PUBLISHED, visibility, pageable);
        return page.map(PortalArticleSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<PortalArticleSummary> browseFeatured(UUID categoryId, Pageable pageable, boolean authenticated) {
        UUID tenantId = TenantContext.get();
        java.util.List<Visibility> visibility = authenticated
                ? java.util.List.of(Visibility.PUBLIC, Visibility.AUTHENTICATED)
                : java.util.List.of(Visibility.PUBLIC);
        Page<Article> page = categoryId != null
                ? articleRepository.findByTenantIdAndStatusAndVisibilityInAndCategoryIdAndFeaturedTrue(tenantId, ArticleStatus.PUBLISHED, visibility, categoryId, pageable)
                : articleRepository.findByTenantIdAndStatusAndVisibilityInAndFeaturedTrue(tenantId, ArticleStatus.PUBLISHED, visibility, pageable);
        return page.map(PortalArticleSummary::from);
    }

    @Transactional
    public PortalArticleDetail getBySlug(String slug, String anonymousSessionId) { return getBySlug(slug, anonymousSessionId, false); }

    @Transactional
    public PortalArticleDetail getBySlug(String slug, String anonymousSessionId, boolean authenticated) {
        Article article = articleRepository.findByTenantIdAndSlugAndStatus(
                        TenantContext.get(), slug, ArticleStatus.PUBLISHED)
                
                .filter(a -> accessPolicy.canReadPortal(a, authenticated))
                .orElseThrow(() -> new IllegalStateException("Article not found or not visible to this caller: " + slug));

        analyticsEventService.record(AnalyticsEventType.ARTICLE_VIEWED, anonymousSessionId, null,
                article.getId(), article.getActiveVersion().getId(), null);

        PortalArticleDetail base = PortalArticleDetail.from(article, article.getActiveVersion());
        var translations = translationLinks.findByTenantIdAndSourceArticleId(TenantContext.get(), article.getId()).stream()
                .map(l -> articleRepository.findByTenantIdAndId(TenantContext.get(), l.getTranslatedArticleId()).orElse(null))
                .filter(a -> accessPolicy.canReadPortal(a, authenticated))
                .map(a -> new PortalArticleDetail.Translation(a.getId(), a.getLanguage(), a.getSlug(), a.getTitle()))
                .toList();
        var tags = tagRepository.findByTenantIdAndArticleIdOrderByTagAsc(TenantContext.get(), article.getId()).stream().map(com.ricozknow.article.ArticleTag::getTag).toList();
        var related = relatedRepository.findByTenantIdAndArticleId(TenantContext.get(), article.getId()).stream()
                .map(r -> articleRepository.findByTenantIdAndId(TenantContext.get(), r.getRelatedArticle().getId()).orElse(null))
                .filter(a -> accessPolicy.canReadPortal(a, authenticated))
                .map(Article::getId).toList();
        return new PortalArticleDetail(base.id(), base.slug(), base.title(), base.categoryId(), base.content(), base.versionNumber(), base.pinned(), base.featured(), tags, related, translations);
    }

    @Transactional
    public void recordFeedback(UUID articleId, boolean helpful, String anonymousSessionId) {
        recordFeedback(articleId, helpful, anonymousSessionId, null);
    }

    @Transactional
    public void recordFeedback(UUID articleId, boolean helpful, String anonymousSessionId, String reason) {
        Article article = articleRepository.findByTenantIdAndId(TenantContext.get(), articleId)
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED && a.getVisibility() == Visibility.PUBLIC)
                .orElseThrow(() -> new IllegalStateException("Article not found or not publicly visible: " + articleId));

        UUID eventId = analyticsEventService.record(
                helpful ? AnalyticsEventType.ARTICLE_HELPFUL : AnalyticsEventType.ARTICLE_UNHELPFUL,
                anonymousSessionId, null, article.getId(),
                article.getActiveVersion() != null ? article.getActiveVersion().getId() : null,
                reason == null ? null : "{\"reason\":\"" + reason.replace("\"", "") + "\"}");
        if (!helpful) {
            String context = article.getTitle() + "\n" + (article.getActiveVersion() != null ? article.getActiveVersion().getContent().toString() : "");
            feedbackIntelligenceService.classify(eventId, article.getId(), reason, context);
        }
    }
}

