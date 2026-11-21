package com.ricozknow.rag;

import com.ricozknow.common.TenantContext;
import com.ricozknow.security.RetrievalAccessGuard;
import com.ricozknow.rag.dto.RagSearchRequest;
import com.ricozknow.rag.dto.RagSearchResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.KnnQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrievalService {

    private final OpenSearchClient client;
    private final EmbeddingService embeddingService;
    private final MeterRegistry meterRegistry;
    private final RetrievalAccessGuard accessGuard;

    @Value("${ricozknow.security.rag.enabled:false}")
    private boolean enabled;

    public RagSearchResponse retrieve(RagSearchRequest request) {

        if (!enabled) {
            throw new IllegalStateException("RAG vector search is disabled");
        }

        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("RAG query must not be empty");
        }

        long started = System.nanoTime();

        String queryText = request.query().trim();

        try {
            /*
             * ---------------------------------------------------------
             * 1. Validate tenant
             * ---------------------------------------------------------
             */
            UUID tenantId = TenantContext.get();

            if (tenantId == null) {
                throw new IllegalStateException(
                        "Tenant context is required for RAG retrieval"
                );
            }

            /*
             * ---------------------------------------------------------
             * 2. Generate embedding
             * ---------------------------------------------------------
             */
            List<Float> vector = embeddingService.embed(queryText);

            if (vector == null || vector.isEmpty()) {
                throw new IllegalStateException(
                        "Embedding service returned an empty vector"
                );
            }

            /*
             * ---------------------------------------------------------
             * 3. Build metadata filters
             * ---------------------------------------------------------
             */
            Query filters = buildFilters(request, tenantId);

            int candidateK = request.resolvedCandidateK();

            /*
             * ---------------------------------------------------------
             * 4. Vector query
             * ---------------------------------------------------------
             *
             * Use List<Float> directly with the OpenSearch client.
             */
            
            float[] vectorArray = new float[vector.size()];

            for (int i = 0; i < vector.size(); i++) {
                vectorArray[i] = vector.get(i);
            }

            KnnQuery knn = new KnnQuery.Builder()
                    .field("embedding")
                    .vector(vectorArray)
                    .k(candidateK)
                    .build();

            /*
             * ---------------------------------------------------------
             * 5. Hybrid search
             * ---------------------------------------------------------
             *
             * Combines:
             *
             *   - vector similarity
             *   - lexical multi-match
             *   - metadata filters
             *
             * The results are subsequently reranked locally.
             */
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(RagIndexInitializer.INDEX_NAME)
                    .size(candidateK)
                    .query(q -> q.bool(b -> b

                            .should(s -> s.knn(knn))

                            .should(s -> s.multiMatch(mm -> mm
                                    .query(queryText)
                                    .fields(
                                            "title^5",
                                            "content^2",
                                            "categoryName^1.5",
                                            "slug"
                                    )
                            ))

                            .minimumShouldMatch("1")

                            .filter(filters)
                    ))
                    .build();

            /*
             * ---------------------------------------------------------
             * 6. Execute OpenSearch query
             * ---------------------------------------------------------
             */
            SearchResponse<RagChunkDocument> response =
                    client.search(
                            searchRequest,
                            RagChunkDocument.class
                    );

            List<Hit<RagChunkDocument>> hits =
                    response.hits().hits();

            /*
             * ---------------------------------------------------------
             * 7. Local hybrid scoring / reranking
             * ---------------------------------------------------------
             */
            List<ScoredChunk> scored = new ArrayList<>();

            List<String> terms = tokenize(queryText);

            for (Hit<RagChunkDocument> hit : hits) {

                RagChunkDocument document = hit.source();

                if (document == null || !accessGuard.publicChunk(document)) {
                    continue;
                }

                double semanticScore =
                        hit.score() == null
                                ? 0.0
                                : hit.score();

                double lexicalScore =
                        lexicalScore(
                                queryText,
                                document,
                                terms
                        );

                double normalizedSemantic =
                        normalizeSemantic(
                                semanticScore,
                                hits
                        );

                double normalizedLexical =
                        Math.min(
                                1.0,
                                lexicalScore / 100.0
                        );

                /*
                 * Hybrid score:
                 *
                 * 70% semantic/vector
                 * 30% lexical
                 */
                double finalScore =
                        (0.70 * normalizedSemantic)
                                + (0.30 * normalizedLexical);

                scored.add(
                        new ScoredChunk(
                                document,
                                semanticScore,
                                lexicalScore,
                                finalScore
                        )
                );
            }

            /*
             * Highest score first.
             */
            scored.sort(
                    Comparator.comparingDouble(
                            ScoredChunk::score
                    ).reversed()
            );

            /*
             * ---------------------------------------------------------
             * 8. Convert results
             * ---------------------------------------------------------
             */
            List<RagSearchResponse.RagSearchResult> results =
                    new ArrayList<>();

            int rank = 1;

            for (ScoredChunk scoredChunk :
                    scored.stream()
                            .limit(request.resolvedTopK())
                            .toList()) {

                RagChunkDocument document =
                        scoredChunk.doc();

                results.add(
                        new RagSearchResponse.RagSearchResult(

                                document.chunkId(),

                                parseUuid(document.articleId()),

                                parseUuid(document.versionId()),

                                document.slug(),

                                document.title(),

                                document.content(),

                                scoredChunk.score(),

                                scoredChunk.semantic(),

                                scoredChunk.lexical(),

                                rank++,

                                document.categoryId(),

                                document.categoryName(),

                                document.visibility(),

                                document.publishedAt(),

                                document.sourceId(),

                                document.sourceType(),

                                document.sourceUrl(),

                                document.language()
                        )
                );
            }

            /*
             * ---------------------------------------------------------
             * 9. Metrics
             * ---------------------------------------------------------
             */
            long latency =
                    (System.nanoTime() - started)
                            / 1_000_000;

            Timer.builder(
                            "ricozknow.rag.retrieval.latency"
                    )
                    .description("RAG retrieval latency")
                    .register(meterRegistry)
                    .record(
                            latency,
                            TimeUnit.MILLISECONDS
                    );

            meterRegistry
                    .counter(
                            "ricozknow.rag.retrieval.queries"
                    )
                    .increment();

            /*
             * ---------------------------------------------------------
             * 10. Response
             * ---------------------------------------------------------
             */
            return new RagSearchResponse(

                    queryText,

                    results,

                    new RagSearchResponse.RetrievalMetadata(
                            "HYBRID_VECTOR_LEXICAL_RERANK",
                            scored.size(),
                            results.size(),
                            latency
                    )
            );

        } catch (Exception ex) {

            meterRegistry
                    .counter(
                            "ricozknow.rag.retrieval.errors"
                    )
                    .increment();

            log.error(
                    "RAG retrieval failed for query: {}",
                    queryText,
                    ex
            );

            throw new IllegalStateException(
                    "RAG retrieval failed",
                    ex
            );
        }
    }

    /**
     * Builds tenant + visibility + category + language + article filters.
     */
    private Query buildFilters(
            RagSearchRequest request,
            UUID tenantId
    ) {

        return new Query.Builder()
                .bool(b -> {

                    /*
                     * -------------------------------------------------
                     * Tenant isolation
                     * -------------------------------------------------
                     */
                    b.filter(f -> f.exists(e -> e.field("articleId")));
                    b.mustNot(f -> f.exists(e -> e.field("sourceId")));
                    b.filter(f -> f.term(t ->
                            t.field("tenantId")
                                    .value(
                                            FieldValue.of(
                                                    tenantId.toString()
                                            )
                                    )
                    ));

                    /*
                     * -------------------------------------------------
                     * Visibility
                     * -------------------------------------------------
                     */
                    Set<String> requested =
                            request.visibilities() == null
                                    ? Set.of()
                                    : request.visibilities();

                    Set<String> visibility =
                            requested.isEmpty()
                                    ? Set.of("PUBLIC")
                                    : requested.stream()
                                            .filter(v ->
                                                    "PUBLIC".equals(v)
                                            )
                                            .collect(Collectors.toSet());

                    if (visibility.isEmpty()) {
                        visibility = Set.of("PUBLIC");
                    }

                    Set<String> finalVisibility = visibility;

                    b.filter(f -> f.terms(t ->
                            t.field("visibility")
                                    .terms(v -> v.value(
                                            finalVisibility.stream()
                                                    .map(FieldValue::of)
                                                    .toList()
                                    ))
                    ));

                    /*
                     * -------------------------------------------------
                     * Category
                     * -------------------------------------------------
                     */
                    if (request.categoryId() != null) {

                        b.filter(f -> f.term(t ->
                                t.field("categoryId")
                                        .value(
                                                FieldValue.of(
                                                        request.categoryId()
                                                                .toString()
                                                )
                                        )
                        ));
                    }

                    /*
                     * -------------------------------------------------
                     * Language
                     *
                     * IMPORTANT:
                     * language is metadata, therefore use TERM rather
                     * than MATCH.
                     * -------------------------------------------------
                     */
                    if (request.language() != null
                            && !request.language().isBlank()) {

                        String language =
                                request.language()
                                        .trim()
                                        .toLowerCase(
                                                Locale.ROOT
                                        );

                        b.filter(f -> f.term(t ->
                                t.field("language")
                                        .value(
                                                FieldValue.of(
                                                        language
                                                )
                                        )
                        ));
                    }

                    /*
                     * -------------------------------------------------
                     * Specific articles
                     * -------------------------------------------------
                     */
                    if (request.articleIds() != null
                            && !request.articleIds().isEmpty()) {

                        b.filter(f -> f.terms(t ->
                                t.field("articleId")
                                        .terms(v -> v.value(
                                                request.articleIds()
                                                        .stream()
                                                        .map(UUID::toString)
                                                        .map(FieldValue::of)
                                                        .toList()
                                        ))
                        ));
                    }

                    return b;
                })
                .build();
    }

    /**
     * Normalizes semantic OpenSearch score between 0 and 1.
     */
    private double normalizeSemantic(
            double value,
            List<Hit<RagChunkDocument>> hits
    ) {

        double max =
                hits.stream()
                        .map(Hit::score)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(1.0);

        if (max <= 0) {
            return 0.0;
        }

        return Math.max(0.0, value) / max;
    }

    /**
     * Simple local lexical score used by the reranker.
     */
    private double lexicalScore(
            String query,
            RagChunkDocument document,
            List<String> terms
    ) {

        String title =
                document.title() == null
                        ? ""
                        : document.title()
                                .toLowerCase(Locale.ROOT);

        String content =
                document.content() == null
                        ? ""
                        : document.content()
                                .toLowerCase(Locale.ROOT);

        String slug =
                document.slug() == null
                        ? ""
                        : document.slug()
                                .toLowerCase(Locale.ROOT);

        String normalizedQuery =
                query.toLowerCase(Locale.ROOT);

        double score = 0.0;

        for (String term : terms) {

            if (title.contains(term)) {
                score += 12;
            }

            if (slug.contains(term)) {
                score += 8;
            }

            if (content.contains(term)) {
                score += 3;
            }
        }

        /*
         * Exact phrase match gets a significant boost.
         */
        if (title.contains(normalizedQuery)) {
            score += 25;
        }

        if (content.contains(normalizedQuery)) {
            score += 15;
        }

        return Math.min(100.0, score);
    }

    /**
     * Tokenizes query text.
     */
    private List<String> tokenize(String text) {

        return Arrays.stream(
                        text.toLowerCase(Locale.ROOT)
                                .split("[^\\p{L}\\p{Nd}]+")
                )
                .filter(token -> token.length() >= 2)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Safely converts an OpenSearch string into UUID.
     */
    private UUID parseUuid(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {

            log.warn(
                    "Invalid UUID in RAG document: {}",
                    value
            );

            return null;
        }
    }

    private record ScoredChunk(
            RagChunkDocument doc,
            double semantic,
            double lexical,
            double score
    ) {
    }
}