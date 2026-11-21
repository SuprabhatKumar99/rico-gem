package com.ricozknow.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.dto.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Tenant-scoped review ownership, deterministic quality checks and human verification. */
@Service @RequiredArgsConstructor
public class ArticleGovernanceService {
    private final ArticleRepository articles;
    private final ArticleVersionRepository versions;
    private final ArticleReviewDecisionRepository decisions;
    private final ArticleVerificationEventRepository events;
    private final ArticleBlockCommentRepository comments;
    private final UserRepository users;
    private final AuditService audit;

    private Article find(UUID id) {
        return articles.findByTenantIdAndId(TenantContext.get(),id).orElseThrow(()->new ArticleNotFoundException(id));
    }
    private UUID actor() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.getPrincipal() instanceof AuthenticatedUser user) return user.userId();
        throw new IllegalStateException("Authentication required");
    }
    private GovernanceResponse response(Article a) {
        Instant due=a.getNextReviewAt();
        return new GovernanceResponse(a.getId(),a.getOwnerId(),a.getReviewIntervalDays(),due,a.getVerifiedAt(),a.getVerifiedBy(),due!=null&&!due.isAfter(Instant.now()));
    }
    @Transactional(readOnly=true)
    public GovernanceResponse get(UUID id) { return response(find(id)); }
    @Transactional
    public GovernanceResponse update(UUID id, GovernanceRequest request) {
        Article a=find(id);
        if(request.ownerId()!=null) {
            var owner=users.findById(request.ownerId()).orElseThrow(()->new IllegalArgumentException("Owner does not exist"));
            if(!owner.getTenantId().equals(TenantContext.get()) || owner.getStatus()!=com.ricozknow.user.User.UserStatus.ACTIVE)
                throw new IllegalArgumentException("Owner must be an active user from this tenant");
            a.setOwnerId(owner.getId());
        }
        if(request.reviewIntervalDays()!=null) {
            int days=request.reviewIntervalDays();
            if(days<1||days>3650) throw new IllegalArgumentException("Review interval must be 1 to 3650 days");
            a.setReviewIntervalDays(days);
            a.setNextReviewAt(Instant.now().plus(Duration.ofDays(days)));
            a.setReviewDueNotifiedAt(null);
        }
        record(a,"GOVERNANCE_SETTINGS_UPDATED",String.valueOf(request),actor());
        return response(a);
    }
    @Transactional(readOnly=true)
    public ContentQualityResponse quality(UUID id) {
        Article a=find(id);
        ArticleVersion version=a.getActiveVersion();
        if(version==null) version=versions.findTopByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(),id).orElse(null);
        if(version==null) return new ContentQualityResponse(id,null,0,false,List.of("No article version exists"));
        List<String> warnings=new ArrayList<>();
        if(a.getOwnerId()==null) warnings.add("No owner assigned");
        if(a.getNextReviewAt()==null || !a.getNextReviewAt().isAfter(Instant.now())) warnings.add("Review is overdue or not scheduled");
        if(comments.existsByTenantIdAndVersionIdAndResolvedFalse(TenantContext.get(),version.getId())) warnings.add("Unresolved reviewer comments");
        JsonNode content=version.getContent();
        JsonNode blocks=content==null?null:content.path("blocks");
        if(blocks==null||!blocks.isArray()||blocks.isEmpty()) warnings.add("Article has no valid content blocks");
        else {
            Set<String> ids=new HashSet<>();
            for(JsonNode block:blocks) {
                String type=block.path("type").asText();
                String blockId=block.path("id").asText("");
                if(blockId.isBlank()||!ids.add(blockId)) warnings.add("Missing or duplicate block identifier");
                if(Set.of("heading","paragraph","code").contains(type) && block.path("text").asText("").isBlank()) warnings.add("Empty "+type+" block");
                if(type.equals("image")) {
                    if(block.path("alt").asText("").isBlank()) warnings.add("Image missing alternative text");
                    if(!safeUrl(block.path("url").asText(""))) warnings.add("Image has an invalid or unsafe URL");
                }
            }
        }
        int score=Math.max(0,100-warnings.size()*15);
        return new ContentQualityResponse(id,version.getId(),score,warnings.isEmpty(),List.copyOf(warnings));
    }
    private boolean safeUrl(String raw) {
        try {URI uri=URI.create(raw);return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))&&uri.getHost()!=null;}
        catch(RuntimeException e){return false;}
    }
    @Transactional
    public GovernanceResponse verify(UUID id) {
        Article a=find(id);
        if(a.getStatus()!=ArticleStatus.PUBLISHED || a.getActiveVersion()==null)
            throw new InvalidArticleStateException("Only published content can be verified");
        ContentQualityResponse report=quality(id);
        if(!report.readyForVerification()) throw new InvalidArticleStateException("Quality checks failed: "+String.join("; ",report.warnings()));
        a.setVerifiedAt(Instant.now()); a.setVerifiedBy(actor());
        a.setNextReviewAt(Instant.now().plus(Duration.ofDays(a.getReviewIntervalDays())));
        a.setReviewDueNotifiedAt(null);
        record(a,"CONTENT_VERIFIED","version="+report.versionId()+",score="+report.score(),actor());
        return response(a);
    }
    private void record(Article a,String action,String details,UUID actorId) {
        ArticleVerificationEvent event=new ArticleVerificationEvent();
        event.setArticle(a);event.setActorId(actorId);event.setAction(action);event.setDetails(details);
        events.save(event);audit.record("Article",a.getId(),action,null,details);
    }
    @Transactional(readOnly=true)
    public List<ReviewHistoryResponse> reviewHistory(UUID articleId) {
        find(articleId);
        return decisions.findByTenantIdAndArticleIdOrderByCreatedAtDesc(TenantContext.get(),articleId)
            .stream().map(d->new ReviewHistoryResponse(d.getId(),d.getVersion().getId(),d.getReviewerId(),d.getDecision(),d.getComment(),d.getCreatedAt())).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> verificationHistory(UUID id) {
        find(id);
        return events.findByTenantIdAndArticleIdOrderByCreatedAtDesc(TenantContext.get(),id).stream()
            .map(e->{Map<String,Object> entry=new LinkedHashMap<>();entry.put("action",e.getAction());entry.put("actorId",e.getActorId());entry.put("details",e.getDetails());entry.put("at",e.getCreatedAt());return entry;}).toList();
    }
    @Transactional(readOnly=true)
    public KnowledgeHealthResponse health() {
        var all=articles.findAllByTenantId(TenantContext.get());
        Instant now=Instant.now();
        List<UUID> due=all.stream().filter(a->a.getNextReviewAt()==null||!a.getNextReviewAt().isAfter(now)).map(Article::getId).toList();
        long stale=all.stream().filter(a->a.getActiveVersion()!=null&&a.getActiveVersion().getExpiresAt()!=null&&a.getActiveVersion().getExpiresAt().isBefore(now)).count();
        return new KnowledgeHealthResponse(all.size(),all.stream().filter(a->a.getStatus()==ArticleStatus.PUBLISHED).count(),
            due.size(),all.stream().filter(a->a.getVerifiedAt()==null).count(),stale,due);
    }
    /** Called from scheduled worker with TenantContext established before transaction begins. */
    @Transactional
    public void signalDueContent() {
        Instant now=Instant.now();
        for(Article article:articles.findAllByTenantId(TenantContext.get())) {
            if(article.getNextReviewAt()!=null&&!article.getNextReviewAt().isAfter(now)
                    &&(article.getReviewDueNotifiedAt()==null||article.getReviewDueNotifiedAt().isBefore(article.getNextReviewAt()))) {
                article.setReviewDueNotifiedAt(now);
                record(article,"CONTENT_REVIEW_DUE","scheduled="+article.getNextReviewAt(),null);
            }
        }
    }
}
