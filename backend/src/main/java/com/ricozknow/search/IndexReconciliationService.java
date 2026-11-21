package com.ricozknow.search;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.common.TenantContext;
import com.ricozknow.publishing.IndexJobService;
import com.ricozknow.publishing.IndexJobRepository;
import com.ricozknow.publishing.IndexJobStatus;
import com.ricozknow.rag.RagChunkDocument;
import com.ricozknow.rag.RagIndexInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexReconciliationService {
    private static final int PAGE_SIZE = 100;

    private final ArticleRepository articleRepository;
    private final OpenSearchClient client;
    private final com.ricozknow.rag.RagIndexingService ragIndexingService;
    private final IndexJobService indexJobService;
    private final IndexJobRepository indexJobRepository;

    public ReconciliationResult reconcileCurrentTenant() {
        UUID tenantId = TenantContext.get();
        int checked = 0, repairsQueued = 0, staleSearch = 0, staleRag = 0, archivedRepairs = 0;

        int page = 0;
        while (true) {
            var articles = articleRepository.findByTenantId(tenantId, PageRequest.of(page++, PAGE_SIZE));
            for (Article article : articles.getContent()) {
                checked++;
                if (article.getStatus() == ArticleStatus.PUBLISHED && article.getActiveVersion() != null) {
                    boolean searchHealthy = searchMatches(tenantId, article);
                    boolean ragHealthy = !ragIndexingService.isEnabled() || ragMatches(tenantId, article);
                    if (!searchHealthy || !ragHealthy) {
                        if (!searchHealthy) staleSearch++;
                        if (!ragHealthy) staleRag++;
                        if (queueRepair(tenantId, article.getId(), article.getActiveVersion().getId())) {
                            repairsQueued++;
                        }
                    }
                } else if (article.getStatus() == ArticleStatus.ARCHIVED) {
                    if (searchExists(tenantId, article.getId()) || (ragIndexingService.isEnabled() && ragMatchesAny(tenantId, article.getId()))) {
                        if (queueRepair(tenantId, article.getId(), null)) {
                            repairsQueued++;
                        }
                        archivedRepairs++;
                    }
                }
            }
            if (!articles.hasNext()) break;
        }

        return new ReconciliationResult(checked, repairsQueued, staleSearch, staleRag, archivedRepairs);
    }

    private boolean queueRepair(UUID tenantId, UUID articleId, UUID versionId) {
        boolean alreadyQueued = indexJobRepository.existsByTenantIdAndArticleIdAndVersionIdAndStatusIn(
                tenantId, articleId, versionId,
                java.util.List.of(IndexJobStatus.PENDING, IndexJobStatus.PROCESSING, IndexJobStatus.RETRYING));
        if (alreadyQueued) return false;
        indexJobService.create(tenantId, articleId, versionId);
        return true;
    }

    private boolean searchMatches(UUID tenantId, Article article) {
        try {
            GetResponse<ArticleSearchDocument> response = client.get(GetRequest.of(g -> g
                    .index(OpenSearchIndexInitializer.INDEX_NAME)
                    .id(tenantId + ":" + article.getId())), ArticleSearchDocument.class);
            return response.found() && response.source() != null
                    && article.getActiveVersion().getId().toString().equals(response.source().versionId);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean searchExists(UUID tenantId, UUID articleId) {
        try {
            return client.exists(e -> e.index(OpenSearchIndexInitializer.INDEX_NAME)
                    .id(tenantId + ":" + articleId)).value();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean ragMatches(UUID tenantId, Article article) {
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(RagIndexInitializer.INDEX_NAME)
                    .size(1)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("tenantId").value(FieldValue.of(tenantId.toString()))))
                            .filter(f -> f.term(t -> t.field("articleId").value(FieldValue.of(article.getId().toString()))))
                            .filter(f -> f.term(t -> t.field("versionId").value(FieldValue.of(article.getActiveVersion().getId().toString()))))
                    ))
                    .build();
            var response = client.search(request, RagChunkDocument.class);
            return response.hits().total() != null && response.hits().total().value() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean ragMatchesAny(UUID tenantId, UUID articleId) {
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(RagIndexInitializer.INDEX_NAME)
                    .size(0)
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("tenantId").value(FieldValue.of(tenantId.toString()))))
                            .filter(f -> f.term(t -> t.field("articleId").value(FieldValue.of(articleId.toString()))))
                    ))
                    .build();
            var response = client.search(request, RagChunkDocument.class);
            return response.hits().total() != null && response.hits().total().value() > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public record ReconciliationResult(int checked, int repairsQueued, int staleSearch, int staleRag, int archivedRepairs) {}
}
