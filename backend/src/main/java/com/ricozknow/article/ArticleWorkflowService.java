package com.ricozknow.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.dto.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.ContentNotificationService;
import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ArticleWorkflowService {
    private final ArticleRepository articles;
    private final ArticleVersionRepository versions;
    private final ArticleTagRepository tags;
    private final ArticleRelatedRepository related;
    private final ArticleBlockCommentRepository commentsRepo;
    private final AuditService audit;
    private final ContentNotificationService notifications;
    private final ArticleService articleService;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly=true)
    public ArticleMetadataResponse metadata(UUID articleId) {
        Article a=find(articleId); UUID t=TenantContext.get();
        return new ArticleMetadataResponse(
            tags.findByTenantIdAndArticleIdOrderByTagAsc(t,articleId).stream().map(ArticleTag::getTag).toList(),
            related.findByTenantIdAndArticleId(t,articleId).stream().map(r->r.getRelatedArticle().getId()).toList(),
            a.isPinned(), a.isFeatured());
    }

    @Transactional
    public ArticleMetadataResponse updateMetadata(UUID articleId, ArticleMetadataRequest req) {
        Article a=find(articleId); UUID t=TenantContext.get();
        tags.deleteByTenantIdAndArticleId(t,articleId);
        if(req.tags()!=null) for(String raw:req.tags()) {
            String tag=raw==null?null:raw.trim().toLowerCase(Locale.ROOT);
            if(tag!=null && !tag.isBlank() && tag.length()<=80) {
                ArticleTag at=new ArticleTag(); at.setArticle(a); at.setTag(tag); tags.save(at);
            }
        }
        related.deleteByTenantIdAndArticleId(t,articleId);
        if(req.relatedArticleIds()!=null) for(UUID rid:req.relatedArticleIds()) {
            if(rid.equals(articleId)) continue;
            Article r=articles.findByTenantIdAndId(t,rid).orElse(null);
            if(r!=null) { ArticleRelated ar=new ArticleRelated(); ar.setArticle(a); ar.setRelatedArticle(r); related.save(ar); }
        }
        if(req.pinned()!=null) a.setPinned(req.pinned());
        if(req.featured()!=null) a.setFeatured(req.featured());
        audit.record("Article",articleId,"ARTICLE_METADATA_UPDATED",null,req);
        return metadata(articleId);
    }

    @Transactional(readOnly=true)
    public List<BlockCommentResponse> comments(UUID articleId, UUID versionId) {
        version(articleId,versionId); return commentsRepo.findByTenantIdAndArticleIdAndVersionIdOrderByCreatedAtAsc(TenantContext.get(),articleId,versionId)
            .stream().map(this::response).toList();
    }

    @Transactional
    public BlockCommentResponse addComment(UUID articleId, UUID versionId, BlockCommentRequest req) {
        Article a=find(articleId);
        ArticleVersion v=version(articleId,versionId);
        if(!containsBlock(v.getContent(),req.blockId())) throw new IllegalArgumentException("Block not found in version");
        ArticleBlockComment parent=null;
        if(req.parentId()!=null) {
            parent=commentsRepo.findByTenantIdAndId(TenantContext.get(),req.parentId()).orElseThrow(()->new IllegalArgumentException("Parent comment not found"));
            if(!parent.getArticle().getId().equals(articleId) || !parent.getVersion().getId().equals(versionId) || !parent.getBlockId().equals(req.blockId()))
                throw new IllegalArgumentException("Comment thread belongs to a different block or version");
        }
        ArticleBlockComment c=new ArticleBlockComment(); c.setParent(parent); c.setArticle(a); c.setVersion(v); c.setBlockId(req.blockId());
        c.setAuthorId(currentUserId()); c.setComment(req.comment().trim()); commentsRepo.save(c);
        audit.record("ArticleVersion",versionId,"BLOCK_REVIEW_COMMENT_ADDED",null,req.comment());
        notifications.notifyReviewParticipants(a, "Review comment added", "A comment was added to \"" + a.getTitle() + "\".");
        return response(c);
    }

    @Transactional
    public BlockCommentResponse resolveComment(UUID articleId, UUID commentId) {
        find(articleId);
        ArticleBlockComment c=commentsRepo.findByTenantIdAndId(TenantContext.get(),commentId).orElseThrow();
        if(!c.getArticle().getId().equals(articleId)) throw new IllegalArgumentException("Comment does not belong to article");
        if(c.isResolved()) return response(c);
        c.setResolved(true); c.setResolvedAt(Instant.now());
        audit.record("ArticleBlockComment",c.getId(),"BLOCK_COMMENT_RESOLVED",null,currentUserId());
        return response(c);
    }

    @Transactional(readOnly=true)
    public VersionDiffResponse diff(UUID articleId, UUID fromId, UUID toId) {
        ArticleVersion beforeVersion = version(articleId, fromId);
        ArticleVersion afterVersion = version(articleId, toId);
        JsonNode from = beforeVersion.getContent() == null ? null : beforeVersion.getContent().path("blocks");
        JsonNode to = afterVersion.getContent() == null ? null : afterVersion.getContent().path("blocks");
        Map<String, JsonNode> before = indexBlocks(from);
        Map<String, JsonNode> after = indexBlocks(to);
        Set<String> ids = new LinkedHashSet<>(before.keySet());
        ids.addAll(after.keySet());
        List<VersionDiffResponse.BlockDiff> out = new ArrayList<>();
        for (String id : ids) {
            JsonNode oldBlock = before.get(id);
            JsonNode newBlock = after.get(id);
            String change = oldBlock == null ? "ADDED" : newBlock == null ? "REMOVED"
                    : !oldBlock.equals(newBlock) ? "CHANGED"
                    : !blockPosition(from,id).equals(blockPosition(to,id)) ? "MOVED" : "UNCHANGED";
            if (!"UNCHANGED".equals(change))
                out.add(new VersionDiffResponse.BlockDiff(id, change, oldBlock, newBlock));
        }
        return new VersionDiffResponse(fromId,toId,out);
    }

    private Integer blockPosition(JsonNode blocks, String id) {
        if(blocks!=null && blocks.isArray()) for(int i=0;i<blocks.size();i++) {
            String candidate=blocks.get(i).path("id").asText("");
            if(candidate.isBlank())candidate="block-index-"+i;
            if(candidate.equals(id))return i;
        }
        return -1;
    }

    /** Match blocks by their stable ID, with index fallback for legacy documents. */
    private Map<String, JsonNode> indexBlocks(JsonNode blocks) {
        Map<String, JsonNode> indexed = new LinkedHashMap<>();
        if (blocks == null || !blocks.isArray()) return indexed;
        for (int i = 0; i < blocks.size(); i++) {
            JsonNode block = blocks.get(i);
            String id = block.path("id").asText("");
            if (id.isBlank()) id = "block-index-" + i;
            // Duplicate IDs must not silently overwrite data in legacy content.
            if (indexed.containsKey(id)) id = id + "-duplicate-" + i;
            indexed.put(id, block);
        }
        return indexed;
    }

    @Transactional
    public ArticleVersion rollback(UUID articleId, RollbackRequest req) {
        Article a=find(articleId); ArticleVersion source=version(articleId,req.versionId());
        ArticleVersion latest=versions.findTopByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(),articleId)
            .orElseThrow();
        ArticleVersion draft=new ArticleVersion(); draft.setArticle(a); draft.setVersionNumber(latest.getVersionNumber()+1);
        draft.setContent(source.getContent().deepCopy()); draft.setStatus(VersionStatus.DRAFT); draft.setCreatedBy(currentUserId());
        draft.setChangeSummary(req.changeSummary()==null||req.changeSummary().isBlank()?"Rollback to v"+source.getVersionNumber():req.changeSummary());
        versions.save(draft);
        audit.record("ArticleVersion",draft.getId(),"VERSION_ROLLBACK_CREATED",source.getId(),draft.getVersionNumber());
        return draft;
    }

    @Transactional
    public ArticleVersion schedulePublish(UUID articleId, UUID versionId, ScheduleRequest req) {
        if(req.at().isBefore(Instant.now())) throw new IllegalArgumentException("Publish time must be in the future");
        ArticleVersion v=version(articleId,versionId);
        if(v.getStatus()!=VersionStatus.APPROVED || v.getReviewedBy()==null) throw new InvalidArticleStateException("Reviewer approval is required before scheduling publication");
        Article a=find(articleId);
        for(ArticleVersion other:versions.findByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(),articleId))
            if(!other.getId().equals(versionId)&&other.getStatus()==VersionStatus.SCHEDULED)
                other.setStatus(VersionStatus.APPROVED);
        v.setStatus(VersionStatus.SCHEDULED); a.setScheduledPublishAt(req.at());
        audit.record("ArticleVersion",versionId,"PUBLISH_SCHEDULED",null,req.at()); return v;
    }

    @Transactional
    public Article scheduleUnpublish(UUID articleId, ScheduleRequest req) {
        if(req.at().isBefore(Instant.now())) throw new IllegalArgumentException("Unpublish time must be in the future");
        Article a=find(articleId); if(a.getStatus()!=ArticleStatus.PUBLISHED) throw new InvalidArticleStateException("Only published articles can be scheduled for unpublish");
        a.setScheduledUnpublishAt(req.at()); audit.record("Article",articleId,"UNPUBLISH_SCHEDULED",null,req.at()); return a;
    }

    @Transactional
    public Article clearSchedule(UUID articleId) { Article a=find(articleId); a.setScheduledPublishAt(null); a.setScheduledUnpublishAt(null);
        versions.findByTenantIdAndArticleIdOrderByVersionNumberDesc(TenantContext.get(),articleId).stream()
            .filter(v->v.getStatus()==VersionStatus.SCHEDULED).forEach(v->v.setStatus(VersionStatus.APPROVED));
        return a; }

    @Transactional
    public void runSchedules(UUID tenantId, Instant now) {
        TenantContext.set(tenantId);
        try {
            List<Article> all=new ArrayList<>(articles.findAllByTenantId(tenantId));
            all.sort(Comparator.comparing(Article::getId));
            for(Article scanned:all){
                if((scanned.getScheduledPublishAt()==null||scanned.getScheduledPublishAt().isAfter(now)) &&
                   (scanned.getScheduledUnpublishAt()==null||scanned.getScheduledUnpublishAt().isAfter(now))) continue;
                Article a=articles.lockForScheduledOperation(tenantId,scanned.getId()).orElse(null);
                if(a==null)continue;
                entityManager.refresh(a); // re-check current schedule under a DB row lock (multi-worker safe)
                if(a.getScheduledPublishAt()!=null && !a.getScheduledPublishAt().isAfter(now)  ){
                    ArticleVersion v=versions.findByTenantIdAndArticleIdOrderByVersionNumberDesc(tenantId,a.getId()).stream().filter(x->x.getStatus()==VersionStatus.SCHEDULED).findFirst().orElse(null);
                    if(v!=null){ articleService.publishScheduledVersion(a.getId(),v.getId()); a.setScheduledPublishAt(null); }
                }
                if(a.getScheduledUnpublishAt()!=null && !a.getScheduledUnpublishAt().isAfter(now) && a.getStatus()==ArticleStatus.PUBLISHED){
                    articleService.unpublishScheduled(a.getId());
                }
            }
        } finally { TenantContext.clear(); }
    }

    private Article find(UUID id){return articles.findByTenantIdAndId(TenantContext.get(),id).orElseThrow(()->new ArticleNotFoundException(id));}
    private ArticleVersion version(UUID articleId,UUID versionId){
        ArticleVersion v=versions.findByTenantIdAndId(TenantContext.get(),versionId).orElseThrow(()->new InvalidArticleStateException("Version not found"));
        if(!v.getArticle().getId().equals(articleId)) throw new InvalidArticleStateException("Version does not belong to article"); return v;
    }
    private boolean containsBlock(JsonNode c,String id){
        JsonNode b=c==null?null:c.get("blocks");
        if(b==null||!b.isArray()) return false;
        for(int i=0;i<b.size();i++){
            JsonNode n=b.get(i);
            if(id.equals(n.path("id").asText()) || id.equals("block-index-"+i)) return true;
        }
        return false;
    }
    private BlockCommentResponse response(ArticleBlockComment c){return new BlockCommentResponse(c.getId(),c.getArticle().getId(),c.getVersion().getId(),c.getBlockId(),c.getAuthorId(),c.getComment(),c.getParent()==null?null:c.getParent().getId(),c.isResolved(),c.getCreatedAt(),c.getResolvedAt());}
    private UUID currentUserId(){var a=SecurityContextHolder.getContext().getAuthentication(); if(a!=null&&a.getPrincipal() instanceof AuthenticatedUser u)return u.userId(); throw new IllegalStateException("No authenticated user");}
}
