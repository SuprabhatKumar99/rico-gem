package com.ricozknow.search;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.common.TenantContext;
import com.ricozknow.publishing.IndexJobEntity;
import com.ricozknow.publishing.IndexJobService;
import com.ricozknow.rag.RagIndexingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Durable index worker. PostgreSQL is the source of truth for jobs; Redis is
 * no longer required for delivery. Jobs survive restarts, Redis loss and
 * transient OpenSearch/embedding failures.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IndexingWorker {
    private final IndexJobService jobService;
    private final SearchIndexingService searchIndexingService;
    private final RagIndexingService ragIndexingService;
    private final IndexStatusService statusService;
    private final ArticleRepository articleRepository;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ricozknow-index-worker");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (stopped.get() || !started.compareAndSet(false, true)) {
            return;
        }
        executor.submit(this::runLoop);
    }

    @EventListener(ContextClosedEvent.class)
    public void onApplicationClosing() {
        stop();
    }

    @PreDestroy
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(7, TimeUnit.SECONDS)) {
                log.warn("Index worker did not terminate within 7 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void runLoop() {
        long lastRecovery = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (System.currentTimeMillis() - lastRecovery > 30_000) {
                    int recovered = jobService.recoverStaleProcessingJobs(600);
                    if (recovered > 0) log.warn("Recovered {} stale indexing job(s)", recovered);
                    lastRecovery = System.currentTimeMillis();
                }
                IndexJobEntity job = jobService.claimNext();
                if (job == null) {
                    Thread.sleep(1000L);
                    continue;
                }
                process(job);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                if (Thread.currentThread().isInterrupted() || stopped.get()) {
                    break;
                }
                log.error("Unexpected indexing worker failure; retrying after a short delay", ex);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void process(IndexJobEntity job) {
        UUID tenantId = job.getTenantId();
        TenantContext.set(tenantId);
        String stage = "SEARCH";
        try {
            if (job.getVersionId() == null) {
                statusService.searchProcessing(tenantId, job.getArticleId());
                searchIndexingService.removeArticle(tenantId, job.getArticleId());
                statusService.markSkipped(tenantId, job.getArticleId());
                stage = "RAG";
                if (ragIndexingService.removeArticle(tenantId, job.getArticleId())) {
                    // Archived content is intentionally absent from the RAG index.
                }
                jobService.complete(job.getId());
                return;
            }

            Article article = articleRepository.findByTenantIdAndId(tenantId, job.getArticleId()).orElse(null);
            if (article == null || article.getActiveVersion() == null ||
                    !job.getVersionId().equals(article.getActiveVersion().getId())) {
                statusService.markSkipped(tenantId, job.getArticleId());
                jobService.complete(job.getId());
                return;
            }

            statusService.searchProcessing(tenantId, job.getArticleId());
            boolean searchIndexed = searchIndexingService.indexVersion(tenantId, job.getArticleId(), job.getVersionId());
            if (searchIndexed) {
                statusService.searchIndexed(tenantId, job.getArticleId());
            } else {
                statusService.markSkipped(tenantId, job.getArticleId());
            }

            if (ragIndexingService.isEnabled()) {
                stage = "RAG";
                statusService.ragProcessing(tenantId, job.getArticleId());
                boolean ragIndexed = ragIndexingService.indexVersion(tenantId, job.getArticleId(), job.getVersionId());
                if (ragIndexed) {
                    statusService.ragIndexed(tenantId, job.getArticleId());
                } else {
                    statusService.markSkipped(tenantId, job.getArticleId());
                }
            } else {
                statusService.markRagSkipped(tenantId, job.getArticleId());
            }

            jobService.complete(job.getId());
        } catch (Exception ex) {
            if ("RAG".equals(stage)) {
                statusService.ragFailed(tenantId, job.getArticleId(), ex);
            } else {
                statusService.searchFailed(tenantId, job.getArticleId(), ex);
            }
            jobService.fail(job.getId(), ex);
            log.error("Index job {} failed on attempt {}", job.getId(), job.getAttemptCount(), ex);
        } finally {
            TenantContext.clear();
        }
    }


}
