package com.ricozknow.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.analytics.AnalyticsEventRepository;
import com.ricozknow.analytics.AnalyticsEventService;
import com.ricozknow.analytics.AnalyticsEventType;
import com.ricozknow.article.Visibility;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagChunkDocument;
import com.ricozknow.security.RetrievalAccessGuard;
import com.ricozknow.search.dto.SearchResultResponse;
import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.json.JsonData;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final OpenSearchClient client;
    private final SynonymService synonymService;
    private final AnalyticsEventService analyticsEventService;
    private final AnalyticsEventRepository analyticsEvents;
    private final QueryRewriteService queryRewriteService;
    private final ObjectMapper objectMapper;
    private final RetrievalAccessGuard accessGuard;

    public SearchResultResponse search(
            String rawQuery,
            UUID categoryId,
            int page,
            int size,
            String anonymousSessionId) {

        return search(
                rawQuery,
                categoryId,
                page,
                size,
                anonymousSessionId,
                Set.of(Visibility.PUBLIC),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SearchResultResponse search(
            String rawQuery,
            UUID categoryId,
            int page,
            int size,
            String anonymousSessionId,
            String language) {

        return search(
                rawQuery,
                categoryId,
                page,
                size,
                anonymousSessionId,
                Set.of(Visibility.PUBLIC),
                language,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SearchResultResponse search(
            String rawQuery,
            UUID categoryId,
            int page,
            int size,
            String anonymousSessionId,
            Set<Visibility> allowedVisibilities) {

        return search(
                rawQuery,
                categoryId,
                page,
                size,
                anonymousSessionId,
                allowedVisibilities,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public SearchResultResponse search(
            String rawQuery,
            UUID categoryId,
            int page,
            int size,
            String anonymousSessionId,
            Set<Visibility> allowedVisibilities,
            String language) {

        return search(
                rawQuery,
                categoryId,
                page,
                size,
                anonymousSessionId,
                allowedVisibilities,
                language,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /**
     * Advanced/federated search.
     */
    public SearchResultResponse search(
            String rawQuery,
            UUID categoryId,
            int page,
            int size,
            String anonymousSessionId,
            Set<Visibility> allowedVisibilities,
            String language,
            String tag,
            String articleType,
            Instant dateFrom,
            Instant dateTo,
            String sourceType,
            String role) {

        String normalized =
                rawQuery == null
                        ? ""
                        : rawQuery.trim();

        if (normalized.isBlank()) {
            return empty(normalized);
        }

        QueryRewriteService.Rewrite rewrite =
                queryRewriteService.rewrite(normalized);

        String retrievalQuery =
                rewrite.query();

        Set<String> expanded =
                new LinkedHashSet<>(
                        synonymService.expand(retrievalQuery)
                );

        expanded.add(retrievalQuery);
        expanded.addAll(rewrite.alternatives());

        try {

            Set<UUID> clicked =
                    anonymousSessionId == null
                            ? Set.of()
                            : new LinkedHashSet<>(
                                    analyticsEvents.clickedArticles(
                                            TenantContext.get(),
                                            anonymousSessionId
                                    )
                            );

            Set<String> roleTypes =
                    roleArticleTypes(role);

            /*
             * ------------------------------------------------------------
             * ARTICLE SEARCH
             * ------------------------------------------------------------
             */

            SearchResponse<ArticleSearchDocument> articleResponse = null;

            if (sourceType == null
                    || sourceType.isBlank()
                    || "ARTICLE".equalsIgnoreCase(sourceType)) {

                articleResponse =
                        client.search(
                                articleRequest(
                                        expanded,
                                        categoryId,
                                        language,
                                        tag,
                                        articleType,
                                        dateFrom,
                                        dateTo,
                                        sourceType,
                                        allowedVisibilities,
                                        clicked,
                                        roleTypes,
                                        page,
                                        size
                                ),
                                ArticleSearchDocument.class
                        );
            }

            List<SearchResultResponse.SearchHit> hits =
                    new ArrayList<>();

            if (articleResponse != null) {

                for (Hit<ArticleSearchDocument> hit :
                        articleResponse.hits().hits()) {

                    ArticleSearchDocument d =
                            hit.source();

                    if (d == null || !accessGuard.portalArticle(uuid(d.articleId), uuid(d.versionId),
                            allowedVisibilities.contains(Visibility.AUTHENTICATED))) {
                        continue;
                    }

                    hits.add(
                            new SearchResultResponse.SearchHit(
                                    uuid(d.articleId),
                                    null,
                                    "ARTICLE",
                                    null,
                                    d.slug,
                                    d.title,
                                    excerpt(hit, d),
                                    uuid(d.categoryId),
                                    d.language,
                                    d.articleType,
                                    d.tags == null
                                            ? List.of()
                                            : d.tags,
                                    score(hit)
                            )
                    );
                }
            }

            /*
             * ------------------------------------------------------------
             * FEDERATED KNOWLEDGE SOURCE SEARCH
             * ------------------------------------------------------------
             *
             * Uploaded documents and synced URLs are stored in
             * knowledge_chunks.
             */

            SearchResponse<RagChunkDocument> sourceResponse = null;

            // Phase 3: legacy knowledge-source chunks have no verified source ACL.
            // Keep them out of the public portal and customer search unconditionally.
            // These remain unavailable until a source-level access policy is assigned.

            if (sourceResponse != null) {

                for (Hit<RagChunkDocument> hit :
                        sourceResponse.hits().hits()) {

                    RagChunkDocument d =
                            hit.source();

                    if (d == null
                            || d.sourceId() == null) {
                        continue;
                    }

                    hits.add(
                            new SearchResultResponse.SearchHit(
                                    uuid(d.articleId()),
                                    d.sourceId(),
                                    d.sourceType(),
                                    d.sourceUrl(),
                                    d.slug() == null
                                            ? d.chunkId()
                                            : d.slug(),
                                    d.title(),
                                    excerptSource(
                                            hit,
                                            d.content()
                                    ),
                                    uuid(d.categoryId()),
                                    d.language(),
                                    "GENERAL",
                                    List.of(),
                                    score(hit)
                            )
                    );
                }
            }

            /*
             * ------------------------------------------------------------
             * COLLAPSE DUPLICATE SOURCE RESULTS
             * ------------------------------------------------------------
             */

            Map<String, SearchResultResponse.SearchHit> collapsed =
                    new LinkedHashMap<>();

            for (SearchResultResponse.SearchHit h : hits) {

                String key =
                        h.sourceId() != null
                                ? "SOURCE:" + h.sourceId()
                                : "ARTICLE:" + String.valueOf(h.articleId());

                collapsed.merge(
                        key,
                        h,
                        (a, b) ->
                                a.score() >= b.score()
                                        ? a
                                        : b
                );
            }

            hits =
                    new ArrayList<>(
                            collapsed.values()
                    );

            hits.sort(
                    Comparator
                            .comparingDouble(
                                    SearchResultResponse.SearchHit::score
                            )
                            .reversed()
            );

            long articleTotal =
                    articleResponse == null
                            || articleResponse.hits().total() == null
                            ? 0
                            : articleResponse
                                    .hits()
                                    .total()
                                    .value();

            long sourceTotal =
                    sourceResponse == null
                            || sourceResponse.hits().total() == null
                            ? 0
                            : sourceResponse
                                    .hits()
                                    .total()
                                    .value();

            /*
             * Both OpenSearch indices are paged independently.
             * Merge first, then perform the final portal page.
             */
            List<SearchResultResponse.SearchHit> merged =
                    hits.stream()
                            .skip((long) page * size)
                            .limit(size)
                            .toList();

            SearchResultResponse result =
                    new SearchResultResponse(
                            normalized,
                            retrievalQuery,
                            merged,
                            hits.size(),
                            buildFacets(hits),
                            relatedQuestions(
                                    normalized,
                                    rewrite.alternatives()
                            )
                    );

            recordSearchEvent(
                    normalized,
                    result,
                    anonymousSessionId
            );

            return result;

        } catch (Exception ex) {

            log.error(
                    "Search query failed for tenant {}",
                    TenantContext.getOrNull(),
                    ex
            );

            SearchResultResponse result =
                    empty(normalized);

            recordSearchEvent(
                    normalized,
                    result,
                    anonymousSessionId
            );

            return result;
        }
    }

    /*
     * ================================================================
     * ARTICLE SEARCH REQUEST
     * ================================================================
     */

    private SearchRequest articleRequest(
            Set<String> terms,
            UUID categoryId,
            String language,
            String tag,
            String articleType,
            Instant dateFrom,
            Instant dateTo,
            String sourceType,
            Set<Visibility> visibilities,
            Set<UUID> clicked,
            Set<String> roleTypes,
            int page,
            int size) {

        return new SearchRequest.Builder()
                .index(
                        OpenSearchIndexInitializer.INDEX_NAME
                )
                .from(0)
                .size(
                        Math.max(
                                size * (page + 1),
                                size
                        )
                )
                .trackTotalHits(
                        t -> t.enabled(true)
                )
                .query(q ->
                        q.bool(bool -> {

                            /*
                             * Search terms.
                             */
                            for (String term : terms) {

                                bool.should(
                                        s ->
                                                s.multiMatch(
                                                        mm ->
                                                                mm.query(term)
                                                                        .fields(
                                                                                "title^4",
                                                                                "headings^3",
                                                                                "keywords^2",
                                                                                "tags^2.5",
                                                                                "categoryName^1.5",
                                                                                "body^1"
                                                                        )
                                                                        .fuzziness("AUTO")
                                                                        .prefixLength(1)
                                                )
                                );
                            }

                            /*
                             * Personalized boost:
                             * previously clicked articles.
                             */
                            for (UUID id : clicked) {

                                bool.should(
                                        s ->
                                                s.term(
                                                        t ->
                                                                t.field("articleId")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        id.toString()
                                                                                )
                                                                        )
                                                                        .boost(1.35f)
                                                )
                                );
                            }

                            /*
                             * Personalized boost:
                             * article types relevant to user role.
                             */
                            for (String type : roleTypes) {

                                bool.should(
                                        s ->
                                                s.term(
                                                        t ->
                                                                t.field("articleType")
                                                                        .value(
                                                                                FieldValue.of(type)
                                                                        )
                                                                        .boost(1.20f)
                                                )
                                );
                            }

                            bool.minimumShouldMatch("1");

                            /*
                             * Tenant isolation.
                             */
                            bool.filter(
                                    f ->
                                            f.term(
                                                    t ->
                                                            t.field("tenantId")
                                                                    .value(
                                                                            FieldValue.of(
                                                                                    TenantContext
                                                                                            .get()
                                                                                            .toString()
                                                                            )
                                                                    )
                                            )
                            );

                            /*
                             * Visibility.
                             */
                            bool.filter(
                                    f ->
                                            f.terms(
                                                    t ->
                                                            t.field("visibility")
                                                                    .terms(
                                                                            v ->
                                                                                    v.value(
                                                                                            visibilities
                                                                                                    .stream()
                                                                                                    .map(
                                                                                                            vv ->
                                                                                                                    FieldValue.of(
                                                                                                                            vv.name()
                                                                                                                    )
                                                                                                    )
                                                                                                    .toList()
                                                                                    )
                                                                    )
                                            )
                            );

                            /*
                             * Category.
                             */
                            if (categoryId != null) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("categoryId")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        categoryId.toString()
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Language.
                             */
                            if (language != null
                                    && !language.isBlank()) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("language")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        language.toLowerCase(
                                                                                                Locale.ROOT
                                                                                        )
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Tag.
                             */
                            if (tag != null
                                    && !tag.isBlank()) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("tags")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        tag.trim()
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Article type.
                             */
                            if (articleType != null
                                    && !articleType.isBlank()) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("articleType")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        articleType
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Date range.
                             */
                            if (dateFrom != null
                                    || dateTo != null) {

                                bool.filter(
                                        f ->
                                                f.range(
                                                        r -> {

                                                            r.field(
                                                                    "publishedAt"
                                                            );

                                                            if (dateFrom != null) {

                                                                r.gte(
                                                                        JsonData.of(
                                                                                dateFrom.toString()
                                                                        )
                                                                );
                                                            }

                                                            if (dateTo != null) {

                                                                r.lte(
                                                                        JsonData.of(
                                                                                dateTo.toString()
                                                                        )
                                                                );
                                                            }

                                                            return r;
                                                        }
                                                )
                                );
                            }

                            /*
                             * Source type.
                             *
                             * Article documents are normally ARTICLE.
                             */
                            if (sourceType != null
                                    && !sourceType.isBlank()) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("sourceType")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        sourceType
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            return bool;
                        })
                )
                .highlight(
                        h ->
                                h.fields(
                                        "body",
                                        f ->
                                                f.fragmentSize(160)
                                                        .numberOfFragments(1)
                                )
                                .fields(
                                        "title",
                                        f ->
                                                f.numberOfFragments(0)
                                )
                )
                .build();
    }

    /*
     * ================================================================
     * FEDERATED SOURCE SEARCH
     * ================================================================
     */

    private SearchRequest sourceRequest(
            Set<String> terms,
            UUID categoryId,
            String language,
            Instant dateFrom,
            Instant dateTo,
            String sourceType,
            Set<Visibility> visibilities,
            int page,
            int size) {

        return new SearchRequest.Builder()
                .index("knowledge_chunks")
                .from(0)
                .size(
                        Math.max(
                                size * (page + 1),
                                size
                        )
                )
                .trackTotalHits(
                        t -> t.enabled(true)
                )
                .query(q ->
                        q.bool(bool -> {

                            /*
                             * Search content.
                             */
                            for (String term : terms) {

                                bool.should(
                                        s ->
                                                s.multiMatch(
                                                        mm ->
                                                                mm.query(term)
                                                                        .fields(
                                                                                "title^3",
                                                                                "content^1"
                                                                        )
                                                                        .fuzziness("AUTO")
                                                                        .prefixLength(1)
                                                )
                                );
                            }

                            bool.minimumShouldMatch("1");

                            /*
                             * Tenant isolation.
                             */
                            bool.filter(
                                    f ->
                                            f.term(
                                                    t ->
                                                            t.field("tenantId")
                                                                    .value(
                                                                            FieldValue.of(
                                                                                    TenantContext
                                                                                            .get()
                                                                                            .toString()
                                                                            )
                                                                    )
                                            )
                            );

                            /*
                             * Only actual knowledge sources.
                             */
                            bool.filter(
                                    f ->
                                            f.exists(
                                                    e ->
                                                            e.field("sourceId")
                                            )
                            );

                            /*
                             * FIX:
                             *
                             * sourceType is now explicitly declared
                             * in this method signature.
                             */
                            if (sourceType != null && !sourceType.isBlank()) {

                                bool.filter(f ->
                                                f.term(
                                                        t ->
                                                                t.field("sourceType")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        sourceType
                                                                                                .trim()
                                                        )
                                                )
                                ));
                            }

                            /*
                             * Visibility.
                             */
                            bool.filter(
                                    f ->
                                            f.terms(
                                                    t ->
                                                            t.field("visibility")
                                                                    .terms(
                                                                            v ->
                                                                                    v.value(
                                                                                            visibilities
                                                                                                    .stream()
                                                                                                    .map(
                                                                                                            vv ->
                                                                                                                    FieldValue.of(
                                                                                                                            vv.name()
                                                                                                                    )
                                                                                                    )
                                                                                                    .toList()
                                                                                    )
                                                                    )
                                            )
                            );

                            /*
                             * Category.
                             */
                            if (categoryId != null) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("categoryId")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        categoryId.toString()
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Language.
                             */
                            if (language != null
                                    && !language.isBlank()) {

                                bool.filter(
                                        f ->
                                                f.term(
                                                        t ->
                                                                t.field("language")
                                                                        .value(
                                                                                FieldValue.of(
                                                                                        language.toLowerCase(
                                                                                                Locale.ROOT
                                                                                        )
                                                                                )
                                                                        )
                                                )
                                );
                            }

                            /*
                             * Published date range.
                             */
                            if (dateFrom != null
                                    || dateTo != null) {

                                bool.filter(
                                        f ->
                                                f.range(
                                                        r -> {

                                                            r.field(
                                                                    "publishedAt"
                                                            );

                                                            if (dateFrom != null) {

                                                                r.gte(
                                                                        JsonData.of(
                                                                                dateFrom.toString()
                                                                        )
                                                                );
                                                            }

                                                            if (dateTo != null) {

                                                                r.lte(
                                                                        JsonData.of(
                                                                                dateTo.toString()
                                                                        )
                                                                );
                                                            }

                                                            return r;
                                                        }
                                                )
                                );
                            }

                            return bool;
                        })
                )
                .highlight(
                        h ->
                                h.fields(
                                        "content",
                                        f ->
                                                f.fragmentSize(160)
                                                        .numberOfFragments(1)
                                )
                )
                .build();
    }

    /*
     * ================================================================
     * FACETS
     * ================================================================
     */

    private SearchResultResponse.Facets buildFacets(
            List<SearchResultResponse.SearchHit> hits) {

        return new SearchResultResponse.Facets(
                facet(
                        hits.stream()
                                .map(
                                        h ->
                                                h.categoryId() == null
                                                        ? null
                                                        : h.categoryId().toString()
                                )
                                .toList()
                ),

                facet(
                        hits.stream()
                                .map(
                                        SearchResultResponse.SearchHit::language
                                )
                                .toList()
                ),

                facet(
                        hits.stream()
                                .flatMap(
                                        h ->
                                                h.tags() == null
                                                        ? java.util.stream.Stream.empty()
                                                        : h.tags().stream()
                                )
                                .toList()
                ),

                facet(
                        hits.stream()
                                .map(
                                        SearchResultResponse.SearchHit::articleType
                                )
                                .toList()
                )
        );
    }

    private List<SearchResultResponse.FacetValue> facet(
            List<String> values) {

        return values.stream()
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .collect(
                        Collectors.groupingBy(
                                v -> v,
                                Collectors.counting()
                        )
                )
                .entrySet()
                .stream()
                .sorted(
                        Map.Entry
                                .<String, Long>comparingByValue()
                                .reversed()
                )
                .limit(20)
                .map(
                        e ->
                                new SearchResultResponse.FacetValue(
                                        e.getKey(),
                                        e.getValue()
                                )
                )
                .toList();
    }

    /*
     * ================================================================
     * RELATED QUESTIONS
     * ================================================================
     */

    private List<String> relatedQuestions(
            String query,
            List<String> alternatives) {

        LinkedHashSet<String> out =
                new LinkedHashSet<>();

        out.addAll(alternatives);

        try {

            analyticsEvents
                    .popularQueries(
                            TenantContext.get()
                    )
                    .stream()
                    .map(r -> String.valueOf(r[0]))
                    .filter(
                            q ->
                                    !q.equalsIgnoreCase(query)
                    )
                    .limit(5)
                    .forEach(out::add);

        } catch (Exception ignored) {
        }

        return out.stream()
                .limit(5)
                .toList();
    }

    /*
     * ================================================================
     * PERSONALIZED ROLE BOOST
     * ================================================================
     */

    private Set<String> roleArticleTypes(
            String role) {

        if (role == null) {
            return Set.of();
        }

        return switch (
                role.toUpperCase(Locale.ROOT)
        ) {

            case "AGENT_VIEWER" ->
                    Set.of(
                            "FAQ",
                            "TROUBLESHOOTING"
                    );

            case "EDITOR", "REVIEWER" ->
                    Set.of(
                            "GUIDE",
                            "POLICY",
                            "REFERENCE"
                    );

            case "ADMIN" ->
                    Set.of(
                            "POLICY",
                            "REFERENCE"
                    );

            default ->
                    Set.of();
        };
    }

    /*
     * ================================================================
     * KNOWLEDGE INDEX
     * ================================================================
     */

    private boolean knowledgeIndexExists() {

        try {

            return client
                    .indices()
                    .exists(
                            e ->
                                    e.index("knowledge_chunks")
                    )
                    .value();

        } catch (Exception ignored) {

            return false;
        }
    }

    /*
     * ================================================================
     * EMPTY RESPONSE
     * ================================================================
     */

    private SearchResultResponse.Facets emptyFacets() {

        return new SearchResultResponse.Facets(
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private SearchResultResponse empty(
            String q) {

        return new SearchResultResponse(
                q,
                q,
                List.of(),
                0,
                emptyFacets(),
                List.of()
        );
    }

    /*
     * ================================================================
     * HELPERS
     * ================================================================
     */

    private double score(Hit<?> h) {

        return h.score() == null
                ? 0
                : h.score();
    }

    private UUID uuid(String s) {

        try {

            return s == null
                    ? null
                    : UUID.fromString(s);

        } catch (Exception e) {

            return null;
        }
    }

    private String excerpt(
            Hit<ArticleSearchDocument> hit,
            ArticleSearchDocument d) {

        var h =
                hit.highlight();

        if (h != null
                && h.containsKey("body")
                && !h.get("body").isEmpty()) {

            return h.get("body").get(0);
        }

        return d.body == null
                ? ""
                : d.body.length() > 160
                        ? d.body.substring(0, 160) + "…"
                        : d.body;
    }

    private String excerptSource(
            Hit<RagChunkDocument> hit,
            String body) {

        var h =
                hit.highlight();

        if (h != null
                && h.containsKey("content")
                && !h.get("content").isEmpty()) {

            return h.get("content").get(0);
        }

        return body == null
                ? ""
                : body.length() > 160
                        ? body.substring(0, 160) + "…"
                        : body;
    }

    /*
     * ================================================================
     * ANALYTICS
     * ================================================================
     */

    private void recordSearchEvent(
            String rawQuery,
            SearchResultResponse result,
            String session) {

        String metadata;

        try {

            metadata =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "query",
                                    rawQuery,

                                    "zeroResult",
                                    result.total() == 0,

                                    "resultCount",
                                    result.total(),

                                    "rewrittenQuery",
                                    result.rewrittenQuery()
                            )
                    );

        } catch (Exception e) {

            metadata =
                    "{\"query\":\""
                            + rawQuery.replace("\"", "")
                            + "\",\"zeroResult\":"
                            + (result.total() == 0)
                            + "}";
        }

        analyticsEventService.record(
                AnalyticsEventType.SEARCH_PERFORMED,
                session,
                currentUserId(),
                null,
                null,
                metadata
        );
    }

    private UUID currentUserId() {

        var auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return auth != null
                && auth.getPrincipal()
                instanceof AuthenticatedUser u
                ? u.userId()
                : null;
    }
}