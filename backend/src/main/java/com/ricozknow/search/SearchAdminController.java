package com.ricozknow.search;

import com.ricozknow.article.ArticleRepository;
import com.ricozknow.common.TenantContext;
import com.ricozknow.publishing.IndexJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SearchAdminController {

    private final IndexRebuildService indexRebuildService;
    private final IndexReconciliationService reconciliationService;
    private final ArticleRepository articleRepository;
    private final IndexJobService indexJobService;

    @PostMapping("/reindex")
    public Map<String, Object> reindex() {
        int count = indexRebuildService.rebuildForCurrentTenant();
        return Map.of("indexed", count);
    }

    @PostMapping("/reconcile")
    public IndexReconciliationService.ReconciliationResult reconcile() {
        return reconciliationService.reconcileCurrentTenant();
    }

    @GetMapping("/index-status")
    public List<IndexStatusResponse> indexStatus(@RequestParam(required = false) UUID articleId) {
        UUID tenantId = TenantContext.get();
        if (articleId != null) {
            return articleRepository.findByTenantIdAndId(tenantId, articleId)
                    .map(a -> List.of(IndexStatusResponse.from(a)))
                    .orElse(List.of());
        }
        return articleRepository.findByTenantId(tenantId, PageRequest.of(0, 200))
                .map(IndexStatusResponse::from).getContent();
    }

    @GetMapping("/jobs/pending")
    public Map<String, Object> pendingJobs() {
        return Map.of("pending", indexJobService.pendingForCurrentTenant());
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        return indexJobService.recentForCurrentTenant().stream().map(job -> {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("id", job.getId());
            result.put("articleId", job.getArticleId());
            result.put("versionId", job.getVersionId());
            result.put("status", job.getStatus());
            result.put("attemptCount", job.getAttemptCount());
            result.put("maxAttempts", job.getMaxAttempts());
            result.put("lastError", job.getLastError());
            result.put("createdAt", job.getCreatedAt());
            result.put("startedAt", job.getStartedAt());
            result.put("completedAt", job.getCompletedAt());
            result.put("nextAttemptAt", job.getNextAttemptAt());
            return result;
        }).toList();
    }
}
