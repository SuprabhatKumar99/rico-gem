package com.ricozknow.rag;

import com.ricozknow.TestJson;
import com.ricozknow.article.*;
import com.ricozknow.article.dto.ArticleCreateRequest;
import com.ricozknow.article.dto.ReviewDecisionRequest;
import com.ricozknow.common.TenantContext;
import com.ricozknow.search.OpenSearchIntegrationTestBase;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantProvisioningService;
import com.ricozknow.user.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "ricozknow.rag.enabled=true",
        "ricozknow.rag.embedding.dimension=3"
})
class RagIndexingIT extends OpenSearchIntegrationTestBase {

    @MockBean
    private EmbeddingService embeddingService;

    @jakarta.annotation.Resource
    private TenantProvisioningService tenantProvisioningService;
    @jakarta.annotation.Resource
    private ArticleService articleService;
    @jakarta.annotation.Resource
    private RagIndexingService ragIndexingService;
    @jakarta.annotation.Resource
    private OpenSearchClient openSearchClient;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        Tenant tenant = tenantProvisioningService.provision("Acme", "acme-" + UUID.randomUUID(), "acme-" + UUID.randomUUID());
        tenantId = tenant.getId();
        TenantContext.set(tenantId);
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), tenantId, "admin@acme.test", null, Set.of("ADMIN"), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
        when(embeddingService.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(java.util.List.of(0.1f, 0.2f, 0.3f));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void indexesPublishedChunksWithTenantAndVersionMetadata() throws Exception {
        Article article = publish("RAG guide", "first paragraph for retrieval");
        boolean indexed = ragIndexingService.indexVersion(tenantId, article.getId(), article.getActiveVersion().getId());

        assertThat(indexed).isTrue();
        var request = new SearchRequest.Builder().index(RagIndexInitializer.INDEX_NAME).size(10)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("tenantId").value(FieldValue.of(tenantId.toString()))))
                        .filter(f -> f.term(t -> t.field("articleId").value(FieldValue.of(article.getId().toString()))))
                )).build();
        var response = openSearchClient.search(request, RagChunkDocument.class);
        assertThat(response.hits().hits()).isNotEmpty();
        assertThat(response.hits().hits().get(0).source().versionId()).isEqualTo(article.getActiveVersion().getId().toString());
    }

    @Test
    void republishingRemovesOldChunksInsteadOfAccumulatingStaleVersions() throws Exception {
        Article article = publish("RAG versioned guide", "version one content");
        UUID v1 = article.getActiveVersion().getId();
        ragIndexingService.indexVersion(tenantId, article.getId(), v1);

        ArticleVersion draft = articleService.createNewDraftVersion(article.getId());
        articleService.updateDraftContent(article.getId(), draft.getId(),
                new com.ricozknow.article.dto.UpdateDraftContentRequest(
                        TestJson.parse("{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"version two content\"}]}") , "replace content"));
        articleService.submitForReview(article.getId(), draft.getId());
        articleService.review(article.getId(), draft.getId(), new ReviewDecisionRequest(true, "ok"));
        Article published = articleService.get(article.getId());
        UUID v2 = published.getActiveVersion().getId();

        ragIndexingService.indexVersion(tenantId, article.getId(), v2);

        var request = new SearchRequest.Builder().index(RagIndexInitializer.INDEX_NAME).size(20)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.term(t -> t.field("tenantId").value(FieldValue.of(tenantId.toString()))))
                        .filter(f -> f.term(t -> t.field("articleId").value(FieldValue.of(article.getId().toString()))))
                )).build();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var response = openSearchClient.search(request, RagChunkDocument.class);
            assertThat(response.hits().hits()).isNotEmpty();
            assertThat(response.hits().hits()).allMatch(h -> v2.toString().equals(h.source().versionId()));
            assertThat(response.hits().hits()).noneMatch(h -> v1.toString().equals(h.source().versionId()));
        });
    }

    private Article publish(String title, String text) {
        Article article = articleService.createDraft(new ArticleCreateRequest(title, "slug-" + UUID.randomUUID(), null,
                TestJson.parse("{\"blocks\":[{\"type\":\"paragraph\",\"text\":\"" + text + "\"}] }")));
        ArticleVersion version = articleService.versionHistory(article.getId()).get(0);
        articleService.submitForReview(article.getId(), version.getId());
        articleService.review(article.getId(), version.getId(), new ReviewDecisionRequest(true, "ok"));
        articleService.setVisibility(article.getId(), Visibility.PUBLIC);
        return articleService.get(article.getId());
    }
}
