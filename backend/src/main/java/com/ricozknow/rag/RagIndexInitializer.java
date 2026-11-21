package com.ricozknow.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagIndexInitializer implements ApplicationRunner {

    public static final String INDEX_NAME = "knowledge_chunks";

    private final OpenSearchClient client;

    @Value("${ricozknow.ai.embedding.dimension:1536}")
    private int dimension;

    @Value("${ricozknow.security.rag.enabled:false}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        if (!enabled) {
            log.info("RAG vector indexing is disabled");
            return;
        }

        boolean exists = client.indices()
                .exists(
                        ExistsRequest.of(
                                e -> e.index(INDEX_NAME)
                        )
                )
                .value();

        if (exists) {
            log.info("OpenSearch RAG index '{}' already exists", INDEX_NAME);
            return;
        }

        CreateIndexRequest request = CreateIndexRequest.of(
                builder -> builder
                        .index(INDEX_NAME)

                        /*
                         * OpenSearch Java Client 2.11.1:
                         * knn() accepts Boolean directly.
                         */
                        .settings(
                                settings -> settings.knn(true)
                        )

                        .mappings(
                                mappings -> mappings

                                        .properties(
                                                "tenantId",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "articleId",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "versionId",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "chunkId",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "slug",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "title",
                                                property -> property.text(
                                                        text -> text.analyzer("standard")
                                                )
                                        )

                                        .properties(
                                                "content",
                                                property -> property.text(
                                                        text -> text.analyzer("standard")
                                                )
                                        )

                                        .properties(
                                                "categoryId",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "categoryName",
                                                property -> property.text(
                                                        text -> text.analyzer("standard")
                                                )
                                        )

                                        .properties(
                                                "visibility",
                                                property -> property.keyword(
                                                        keyword -> keyword
                                                )
                                        )

                                        .properties(
                                                "publishedAt",
                                                property -> property.date(
                                                        date -> date
                                                )
                                        )

                                        .properties("sourceId", property -> property.keyword(keyword -> keyword))
                                        .properties("sourceType", property -> property.keyword(keyword -> keyword))
                                        .properties("sourceUrl", property -> property.keyword(keyword -> keyword))
                                        .properties("language", property -> property.keyword(keyword -> keyword))

                                        .properties(
                                                "chunkIndex",
                                                property -> property.integer(
                                                        integer -> integer
                                                )
                                        )

                                        .properties(
                                                "embedding",
                                                property -> property.knnVector(
                                                        vector -> vector.dimension(dimension)
                                                )
                                        )
                        )
        );

        client.indices().create(request);

        log.info(
                "Created OpenSearch RAG index '{}' with dimension {}",
                INDEX_NAME,
                dimension
        );
    }
}