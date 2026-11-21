package com.ricozknow.article;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ricozknow.article.event.ArticleArchivedEvent;
import com.ricozknow.article.event.ArticlePublishedEvent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.article.dto.ArticleCreateRequest;
import com.ricozknow.article.dto.ReviewDecisionRequest;
import com.ricozknow.article.dto.UpdateDraftContentRequest;
import com.ricozknow.audit.AuditService;
import com.ricozknow.category.CategoryRepository;
import com.ricozknow.enterprise.group.GroupRepository;
import com.ricozknow.common.TenantContext;
import com.ricozknow.publishing.IndexJobPublisher;
import com.ricozknow.notification.ContentNotificationService;
import com.ricozknow.user.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

/**
 * Owns the article lifecycle end to end (spec section 9):
 *
 *   DRAFT --submit--> IN_REVIEW --approve--> PUBLISHED --expire/archive--> ARCHIVED
 *                         |
 *                       reject
 *                         v
 *                       DRAFT
 *
 * Published versions are immutable: editing a published article always creates
 * a new DRAFT version rather than mutating the published row.
 */
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final com.ricozknow.billing.BillingService billing;
    private final ArticleRepository articleRepository;
    private final ArticleVersionRepository versionRepository;
    private final CategoryRepository categoryRepository;
    private final ContentValidator contentValidator;
    private final AuditService auditService;
    private final IndexJobPublisher indexJobPublisher;
    private final ContentNotificationService contentNotifications;
    private final GroupRepository groupRepository;
    private final ArticleReviewDecisionRepository reviewDecisions;
    private final ArticleBlockCommentRepository blockComments;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${ricozknow.article.require-independent-review:false}")
    private boolean requireIndependentReview;

    @Transactional(readOnly = true)
    public Page<Article> list(ArticleStatus status, Pageable pageable) {
        UUID tenantId = TenantContext.get();
        return status != null
                ? articleRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : articleRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Article get(UUID id) {
        return articleRepository.findByTenantIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ArticleVersion> versionHistory(UUID articleId) {
        return versionRepository.findByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(), articleId);
    }

    @Transactional
    public Article createDraft(ArticleCreateRequest request) {
        UUID tenantId = TenantContext.get();
        normalizeBlockIds(request.content());
        contentValidator.validate(request.content());

        if (articleRepository.existsByTenantIdAndSlug(tenantId, request.slug())) {
            throw new InvalidArticleStateException("An article with slug '" + request.slug() + "' already exists");
        }

        billing.checkCapacity(tenantId, "max_articles");
        Article article = new Article();
        article.setTitle(request.title());
        article.setSlug(request.slug());
        article.setLanguage(request.language() == null || request.language().isBlank() ? "en" : request.language().trim().toLowerCase(java.util.Locale.ROOT));
        article.setArticleType(request.articleType() == null ? ArticleType.GENERAL : request.articleType());
        article.setStatus(ArticleStatus.DRAFT);
        article.setCreatedBy(currentUserId());
        article.setOwnerId(currentUserId());
        article.setNextReviewAt(Instant.now().plus(java.time.Duration.ofDays(180)));
        if (request.categoryId() != null) {
            article.setCategory(categoryRepository.findByTenantIdAndId(tenantId, request.categoryId())
                    .orElseThrow(() -> new InvalidArticleStateException("Unknown category: " + request.categoryId())));
        }
        article = articleRepository.save(article);

        ArticleVersion version = new ArticleVersion();
        version.setArticle(article);
        version.setVersionNumber(1);
        version.setContent(request.content());
        version.setStatus(VersionStatus.DRAFT);
        version.setCreatedBy(currentUserId());
        versionRepository.save(version);

        auditService.record("Article", article.getId(), "ARTICLE_CREATED", null, request.title());
        eventPublisher.publishEvent(new com.ricozknow.automation.AutomationEvent(tenantId, "ARTICLE_CREATED", article.getId(), "article-created:"+article.getId()));
        return article;
    }

    @Transactional
    public ArticleVersion updateDraftContent(
            UUID articleId,
            UUID versionId,
            UpdateDraftContentRequest request) {

        normalizeBlockIds(request.content());
        contentValidator.validate(request.content());

        ArticleVersion version =
                getVersion(articleId, versionId);

        if (version.getStatus() != VersionStatus.DRAFT && version.getStatus() != VersionStatus.REJECTED) {
            throw new InvalidArticleStateException("Only a draft or rejected version may be edited");
        }
        if (version.isImmutable()) {
            throw new InvalidArticleStateException(
                    "Cannot edit a published version; create a new draft version instead"
            );
        }

        JsonNode oldContent = version.getContent();

        version.setContent(
                request.content()
        );

        version.setChangeSummary(
                request.changeSummary()
        );

        version.setStatus(
                VersionStatus.DRAFT
        );

        auditService.record(
                "ArticleVersion",
                version.getId(),
                "VERSION_CONTENT_UPDATED",
                oldContent,
                request.content()
        );
        eventPublisher.publishEvent(new com.ricozknow.automation.AutomationEvent(TenantContext.get(), "ARTICLE_UPDATED", articleId, "version-updated:"+versionId+":"+UUID.randomUUID()));

        return version;
    }

    /** Starts a new editable revision on top of the currently published version. */
    @Transactional
    public ArticleVersion createNewDraftVersion(UUID articleId) {
        Article article = get(articleId);
        ArticleVersion latest = versionRepository
                .findTopByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(), articleId)
                .orElseThrow(() -> new InvalidArticleStateException("Article has no versions"));

        ArticleVersion draft = new ArticleVersion();
        draft.setArticle(article);
        draft.setVersionNumber(latest.getVersionNumber() + 1);
        draft.setContent(latest.getContent().deepCopy()); // seed from the last version; caller edits from here
        draft.setStatus(VersionStatus.DRAFT);
        draft.setCreatedBy(currentUserId());
        versionRepository.save(draft);

        auditService.record("ArticleVersion", draft.getId(), "DRAFT_VERSION_CREATED", null, draft.getVersionNumber());
        return draft;
    }

    @Transactional
    public ArticleVersion submitForReview(UUID articleId, UUID versionId) {
        Article article = get(articleId);
        ArticleVersion version = getVersion(articleId, versionId);

        if (version.getStatus() != VersionStatus.DRAFT) {
            throw new InvalidArticleStateException("Only draft versions can be submitted for review");
        }

        version.setStatus(VersionStatus.IN_REVIEW);
        if (article.getStatus() != ArticleStatus.PUBLISHED) article.setStatus(ArticleStatus.IN_REVIEW);

        auditService.record("ArticleVersion", version.getId(), "SUBMITTED_FOR_REVIEW", null, null);
        contentNotifications.notifyAwaitingReview(article);
        return version;
    }

    /** Reviewer decision. Approve publishes the version; reject sends it back to draft. */
    @Transactional
    public ArticleVersion review(UUID articleId, UUID versionId, ReviewDecisionRequest request) {
        Article article = get(articleId);
        ArticleVersion version = versionRepository.lockForCollaboration(TenantContext.get(),versionId)
                .orElseThrow(() -> new InvalidArticleStateException("Version unavailable"));
        if (!version.getArticle().getId().equals(articleId)) throw new InvalidArticleStateException("Wrong article");

        if (version.getStatus() != VersionStatus.IN_REVIEW) {
            throw new InvalidArticleStateException("Only versions in review can be approved or rejected");
        }

        return recordDecision(article, version, request, true);
    }

    /** Approves a reviewed revision without publishing until the approved scheduled time. */
    @Transactional
    public ArticleVersion approveForScheduling(UUID articleId, UUID versionId, String comment) {
        Article article = get(articleId);
        ArticleVersion version = versionRepository.lockForCollaboration(TenantContext.get(),versionId)
            .orElseThrow(()->new InvalidArticleStateException("Version unavailable"));
        if(!version.getArticle().getId().equals(articleId))throw new InvalidArticleStateException("Wrong article");
        return recordDecision(article, version, new ReviewDecisionRequest(true, comment), false);
    }

    private ArticleVersion recordDecision(Article article, ArticleVersion version,
                                          ReviewDecisionRequest request, boolean publishImmediately) {
        if (version.getStatus() != VersionStatus.IN_REVIEW)
            throw new InvalidArticleStateException("Version is not pending review");
        if (Boolean.TRUE.equals(request.approve()) && blockComments.existsByTenantIdAndVersionIdAndResolvedFalse(TenantContext.get(), version.getId()))
            throw new InvalidArticleStateException("Resolve all review comments before approving");
        UUID reviewer = currentUserId();
        if (requireIndependentReview && Boolean.TRUE.equals(request.approve()) && reviewer.equals(version.getCreatedBy()))
            throw new InvalidArticleStateException("Independent review is required for approval");
        version.setReviewedBy(reviewer);
        version.setReviewedAt(Instant.now());
        ArticleReviewDecision log = new ArticleReviewDecision();
        log.setArticle(article); log.setVersion(version); log.setReviewerId(reviewer);
        log.setDecision(Boolean.TRUE.equals(request.approve()) ? (publishImmediately ? "APPROVE_PUBLISH" : "APPROVE_SCHEDULE") : "REJECT");
        log.setComment(request.comment());
        reviewDecisions.save(log);
        if (Boolean.TRUE.equals(request.approve())) {
            if (publishImmediately) publishVersion(article, version);
            else version.setStatus(VersionStatus.APPROVED);
        } else {
            version.setStatus(VersionStatus.REJECTED);
            if (article.getStatus() != ArticleStatus.PUBLISHED) article.setStatus(ArticleStatus.DRAFT);
        }
        auditService.record("ArticleVersion",version.getId(),log.getDecision(),null,request.comment());
        return version;
    }

    @Transactional
    public Article unpublishScheduled(UUID articleId) {
        Article article = get(articleId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setScheduledUnpublishAt(null);
        indexJobPublisher.enqueueRemovalJob(TenantContext.get(), articleId);
        auditService.record("Article", articleId, "ARTICLE_UNPUBLISHED", null, null);
        return article;
    }

    /** Publishes an approved/scheduled version through the same indexing/event path as reviewer approval. */
    @Transactional
    public ArticleVersion publishScheduledVersion(UUID articleId, UUID versionId) {
        Article article = get(articleId);
        ArticleVersion version = getVersion(articleId, versionId);
        if (version.getStatus() != VersionStatus.SCHEDULED || version.getReviewedBy()==null) {
            throw new InvalidArticleStateException("Only approved scheduled versions can be published");
        }
        publishVersion(article, version);
        return version;
    }

    /** Publishes a version: marks it immutable, makes it the article's active version, enqueues indexing. */
    private void publishVersion(Article article, ArticleVersion version) {
        version.setStatus(VersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());

        article.setStatus(ArticleStatus.PUBLISHED);
        article.setActiveVersion(version);
        article.setVerifiedAt(null);
        article.setVerifiedBy(null);
        article.setReviewDueNotifiedAt(null);
        article.setNextReviewAt(Instant.now().plus(java.time.Duration.ofDays(article.getReviewIntervalDays())));
        article.setScheduledPublishAt(null);

        auditService.record("Article", article.getId(), "ARTICLE_PUBLISHED", null, version.getVersionNumber());
        indexJobPublisher.enqueueIndexJob(TenantContext.get(), article.getId(), version.getId());
    
        eventPublisher.publishEvent(new ArticlePublishedEvent(
                TenantContext.get(), article.getId(), article.getTitle(), article.getSlug(), version.getVersionNumber()));
    
    }

    @Transactional
    public void archive(UUID articleId) {
        Article article = get(articleId);
        article.setStatus(ArticleStatus.ARCHIVED);
        auditService.record("Article", articleId, "ARTICLE_ARCHIVED", null, null);
        // Archived articles must disappear from customer search (spec section 9 rule 8).
        indexJobPublisher.enqueueRemovalJob(TenantContext.get(), articleId);
        eventPublisher.publishEvent(new ArticleArchivedEvent(
                TenantContext.get(), article.getId(), article.getTitle(), article.getSlug()));
    }


    @Transactional
    public void setVisibility(
            UUID articleId,
            Visibility visibility
    ) {
        Article article = get(articleId);

        Visibility old = article.getVisibility();

        if (old == visibility) {
            return;
        }

        article.setVisibility(visibility);

        auditService.record(
                "Article",
                articleId,
                "VISIBILITY_CHANGED",
                old,
                visibility
        );

        if (article.getStatus() == ArticleStatus.PUBLISHED
                && article.getActiveVersion() != null) {

            indexJobPublisher.enqueueIndexJob(
                    TenantContext.get(),
                    article.getId(),
                    article.getActiveVersion().getId()
            );
        }
    }

    @Transactional
    public void setVisibilityGroup(UUID articleId, UUID groupId) {
        Article article=get(articleId); var group=groupRepository.findByTenantIdAndId(TenantContext.get(),groupId).orElseThrow(()->new InvalidArticleStateException("Group not found"));
        article.setVisibility(Visibility.GROUP); article.setVisibilityGroup(group); auditService.record("Article",articleId,"GROUP_VISIBILITY_CHANGED",null,groupId);
        if(article.getStatus()==ArticleStatus.PUBLISHED && article.getActiveVersion()!=null) indexJobPublisher.enqueueIndexJob(TenantContext.get(),articleId,article.getActiveVersion().getId());
    }

    private ArticleVersion getVersion(UUID articleId, UUID versionId) {
        ArticleVersion version = versionRepository.findByTenantIdAndId(TenantContext.get(), versionId)
                .orElseThrow(() -> new InvalidArticleStateException("Version not found: " + versionId));
        if (!version.getArticle().getId().equals(articleId)) {
            throw new InvalidArticleStateException("Version does not belong to article " + articleId);
        }
        return version;
    }

    private void normalizeBlockIds(JsonNode content) {
        if (content == null || !content.isObject()) return;
        JsonNode blocks = content.get("blocks");
        if (blocks == null || !blocks.isArray()) return;
        for (JsonNode block : blocks) {
            if (block instanceof ObjectNode object && (!object.hasNonNull("id") || object.get("id").asText().isBlank())) {
                object.put("id", "block-" + UUID.randomUUID());
            }
        }
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal.userId();
        }
        throw new IllegalStateException("No authenticated user in context");
    }
}
