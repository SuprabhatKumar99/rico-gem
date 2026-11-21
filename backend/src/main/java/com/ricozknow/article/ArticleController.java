package com.ricozknow.article;

import com.ricozknow.article.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final ArticleTemplateService templateService;
    private final ArticleGovernanceService governanceService;
    private final ArticleWorkflowService workflowService;

    @GetMapping
    public Page<ArticleResponse> list(@RequestParam(required = false) ArticleStatus status, Pageable pageable) {
        return articleService.list(status, pageable).map(ArticleResponse::from);
    }

    @GetMapping("/{id}")
    public ArticleResponse get(@PathVariable UUID id) {
        return ArticleResponse.from(articleService.get(id));
    }

    @GetMapping("/{id}/versions")
    public List<ArticleVersionResponse> versions(@PathVariable UUID id) {
        return articleService.versionHistory(id).stream().map(ArticleVersionResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ArticleResponse> create(@Valid @RequestBody ArticleCreateRequest request) {
        return ResponseEntity.ok(ArticleResponse.from(articleService.createDraft(request)));
    }

    @PutMapping("/{articleId}/versions/{versionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ArticleVersionResponse updateDraft(@PathVariable UUID articleId,
                                               @PathVariable UUID versionId,
                                               @Valid @RequestBody UpdateDraftContentRequest request) {
        return ArticleVersionResponse.from(articleService.updateDraftContent(articleId, versionId, request));
    }

    @PostMapping("/{articleId}/versions")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ArticleVersionResponse newDraftVersion(@PathVariable UUID articleId) {
        return ArticleVersionResponse.from(articleService.createNewDraftVersion(articleId));
    }

    @PostMapping("/{articleId}/versions/{versionId}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ArticleVersionResponse submitForReview(@PathVariable UUID articleId, @PathVariable UUID versionId) {
        return ArticleVersionResponse.from(articleService.submitForReview(articleId, versionId));
    }

    @PostMapping("/{articleId}/versions/{versionId}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'REVIEWER')")
    public ArticleVersionResponse review(@PathVariable UUID articleId,
                                          @PathVariable UUID versionId,
                                          @Valid @RequestBody ReviewDecisionRequest request) {
        return ArticleVersionResponse.from(articleService.review(articleId, versionId, request));
    }

    @PostMapping("/{articleId}/versions/{versionId}/approve-for-schedule")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ArticleVersionResponse approveForSchedule(@PathVariable UUID articleId,@PathVariable UUID versionId,
            @RequestBody(required=false) ReviewDecisionRequest request) {
        return ArticleVersionResponse.from(articleService.approveForScheduling(articleId, versionId, request==null?null:request.comment()));
    }

    @GetMapping("/{articleId}/review-history")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<ReviewHistoryResponse> reviewHistory(@PathVariable UUID articleId) { return governanceService.reviewHistory(articleId); }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        articleService.archive(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/visibility")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Void> setVisibility(@PathVariable UUID id, @RequestParam Visibility visibility) {
        articleService.setVisibility(id, visibility);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/visibility-group")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void setVisibilityGroup(@PathVariable UUID id, @RequestParam UUID groupId) { articleService.setVisibilityGroup(id, groupId); }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<ArticleTemplateResponse> templates() { return templateService.list(); }

    @GetMapping("/{articleId}/metadata")
    public ArticleMetadataResponse metadata(@PathVariable UUID articleId) { return workflowService.metadata(articleId); }

    @PutMapping("/{articleId}/metadata")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleMetadataResponse metadata(@PathVariable UUID articleId, @Valid @RequestBody ArticleMetadataRequest request) {
        return workflowService.updateMetadata(articleId, request);
    }

    @GetMapping("/{articleId}/versions/{versionId}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<BlockCommentResponse> comments(@PathVariable UUID articleId,@PathVariable UUID versionId) {
        return workflowService.comments(articleId, versionId);
    }

    @PostMapping("/{articleId}/versions/{versionId}/comments")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public BlockCommentResponse addComment(@PathVariable UUID articleId,@PathVariable UUID versionId,@Valid @RequestBody BlockCommentRequest request) {
        return workflowService.addComment(articleId, versionId, request);
    }

    @PostMapping("/{articleId}/comments/{commentId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public BlockCommentResponse resolveComment(@PathVariable UUID articleId,@PathVariable UUID commentId) {
        return workflowService.resolveComment(articleId, commentId);
    }

    @GetMapping("/{articleId}/versions/diff")
    public VersionDiffResponse diff(@PathVariable UUID articleId,@RequestParam UUID fromVersionId,@RequestParam UUID toVersionId) {
        return workflowService.diff(articleId, fromVersionId, toVersionId);
    }

    @PostMapping("/{articleId}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleVersionResponse rollback(@PathVariable UUID articleId,@RequestBody RollbackRequest request) {
        return ArticleVersionResponse.from(workflowService.rollback(articleId, request));
    }

    @PostMapping("/{articleId}/versions/{versionId}/schedule-publish")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleVersionResponse schedulePublish(@PathVariable UUID articleId,@PathVariable UUID versionId,@Valid @RequestBody ScheduleRequest request) {
        return ArticleVersionResponse.from(workflowService.schedulePublish(articleId,versionId,request));
    }

    @PostMapping("/{articleId}/schedule-unpublish")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleResponse scheduleUnpublish(@PathVariable UUID articleId,@Valid @RequestBody ScheduleRequest request) {
        return ArticleResponse.from(workflowService.scheduleUnpublish(articleId,request));
    }

    @DeleteMapping("/{articleId}/schedule")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleResponse clearSchedule(@PathVariable UUID articleId) { return ArticleResponse.from(workflowService.clearSchedule(articleId)); }

}
