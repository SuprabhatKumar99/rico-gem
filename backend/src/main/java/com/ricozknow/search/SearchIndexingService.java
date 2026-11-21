package com.ricozknow.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleTagRepository;
import com.ricozknow.article.ArticleVersion;
import com.ricozknow.article.ArticleVersionRepository;
import com.ricozknow.article.VersionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchIndexingService {

    private final OpenSearchClient client;
    private final ArticleRepository articleRepository;
    private final ArticleVersionRepository versionRepository;
    private final ArticleTagRepository tagRepository;

    /**
     * Index the currently active published version of an article.
     *
     * Returns false when the article/version no longer exists or the supplied
     * version is not the active published version.
     */
    @Transactional(readOnly = true)
    public boolean indexVersion(
            UUID tenantId,
            UUID articleId,
            UUID versionId) {

        Article article = articleRepository
                .findByTenantIdAndId(tenantId, articleId)
                .orElse(null);

        ArticleVersion version = versionRepository
                .findByTenantIdAndId(tenantId, versionId)
                .orElse(null);

        if (article == null || version == null) {
            log.warn(
                    "Skipping index job: article {} or version {} no longer exists",
                    articleId,
                    versionId
            );
            return false;
        }

        UUID activeVersionId = article.getActiveVersion() != null
                ? article.getActiveVersion().getId()
                : null;

        if (version.getStatus() != VersionStatus.PUBLISHED
                || !version.getId().equals(activeVersionId)) {

            log.info(
                    "Skipping index job for {}: version is no longer the active published version",
                    articleId
            );
            return false;
        }

        ArticleSearchDocument doc =
                buildDocument(tenantId, article, version);

        /*
         * Stable document ID:
         *
         * One OpenSearch document per tenant/article.
         * Republishing an article overwrites the previous indexed version.
         */
        String docId =
                tenantId + ":" + article.getId();

        try {

            client.index(IndexRequest.of(builder -> builder
                    .index(OpenSearchIndexInitializer.INDEX_NAME)
                    .id(docId)
                    .document(doc)
            ));

            log.debug(
                    "Indexed article {} version {} for tenant {}",
                    articleId,
                    versionId,
                    tenantId
            );

            return true;

        } catch (Exception ex) {

            log.error(
                    "Failed to index article {} version {}",
                    articleId,
                    versionId,
                    ex
            );

            throw new IndexingFailedException(ex);
        }
    }

    /**
     * Remove an article from OpenSearch.
     */
    @Transactional(readOnly = true)
    public void removeArticle(
            UUID tenantId,
            UUID articleId) {

        String docId =
                tenantId + ":" + articleId;

        try {

            boolean exists = client
                    .exists(builder -> builder
                            .index(OpenSearchIndexInitializer.INDEX_NAME)
                            .id(docId)
                    )
                    .value();

            if (!exists) {
                return;
            }

            client.delete(
                    DeleteRequest.of(builder -> builder
                            .index(OpenSearchIndexInitializer.INDEX_NAME)
                            .id(docId)
                    )
            );

            log.debug(
                    "Removed article {} from search index",
                    articleId
            );

        } catch (Exception ex) {

            log.error(
                    "Failed to remove article {} from index",
                    articleId,
                    ex
            );

            throw new IndexingFailedException(ex);
        }
    }

    /**
     * Convert the PostgreSQL article/version data into the OpenSearch
     * ArticleSearchDocument.
     */
    private ArticleSearchDocument buildDocument(
            UUID tenantId,
            Article article,
            ArticleVersion version) {

        List<String> headings =
                extractHeadings(version.getContent());

        String body =
                extractBodyText(version.getContent());

        /*
         * Load article tags.
         *
         * This was missing in the previous implementation and caused:
         *
         *   cannot find symbol: variable tags
         */
        List<String> tags = tagRepository
                .findByTenantIdAndArticleIdOrderByTagAsc(
                        tenantId,
                        article.getId()
                )
                .stream()
                .map(tag -> tag.getTag())
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .toList();

        String categoryId =
                article.getCategory() != null
                        ? article.getCategory().getId().toString()
                        : null;

        String categoryName =
                article.getCategory() != null
                        ? article.getCategory().getName()
                        : null;

        String visibility =
                article.getVisibility() != null
                        ? article.getVisibility().name()
                        : null;

        String publishedAt =
                version.getPublishedAt() != null
                        ? version.getPublishedAt().toString()
                        : null;

        String language =
                article.getLanguage();

        String articleType =
                article.getArticleType() != null
                        ? article.getArticleType().name()
                        : null;

        return new ArticleSearchDocument(
                tenantId.toString(),
                article.getId().toString(),
                version.getId().toString(),
                article.getSlug(),
                article.getTitle(),
                headings,
                body,

                // Keywords are currently not generated.
                List.of(),

                // Article tags.
                tags,

                categoryId,
                categoryName,
                visibility,
                publishedAt,
                language,
                articleType,

                // Federated search metadata.
                "ARTICLE",
                null
        );
    }

    /**
     * Extract heading text from article block content.
     */
    private List<String> extractHeadings(
            JsonNode content) {

        List<String> headings =
                new ArrayList<>();

        forEachBlock(
                content,
                block -> {

                    String type =
                            textOrNull(block, "type");

                    String text =
                            textOrNull(block, "text");

                    if ("heading".equals(type)
                            && text != null
                            && !text.isBlank()) {

                        headings.add(text.trim());
                    }
                }
        );

        return headings;
    }

    /**
     * Extract searchable body text from article blocks.
     */
    private String extractBodyText(
            JsonNode content) {

        StringBuilder body =
                new StringBuilder();

        forEachBlock(
                content,
                block -> {

                    String type =
                            textOrNull(block, "type");

                    String text =
                            textOrNull(block, "text");

                    if (text != null
                            && !text.isBlank()
                            && (
                            "paragraph".equals(type)
                                    || "code".equals(type)
                    )) {

                        if (body.length() > 0) {
                            body.append(' ');
                        }

                        body.append(text.trim());
                    }
                }
        );

        return body.toString().trim();
    }

    /**
     * Iterate over article content blocks.
     *
     * Expected structure:
     *
     * {
     *   "blocks": [
     *      {
     *          "type": "heading",
     *          "text": "..."
     *      }
     *   ]
     * }
     */
    private void forEachBlock(
            JsonNode content,
            Consumer<JsonNode> consumer) {

        if (content == null
                || !content.isObject()) {
            return;
        }

        JsonNode blocks =
                content.get("blocks");

        if (blocks != null
                && blocks.isArray()) {

            blocks.forEach(consumer);
        }
    }

    /**
     * Safely read a textual JSON property.
     */
    private String textOrNull(
            JsonNode node,
            String field) {

        if (node == null
                || field == null) {
            return null;
        }

        JsonNode value =
                node.get(field);

        if (value == null
                || !value.isValueNode()) {
            return null;
        }

        String text =
                value.asText();

        return text != null
                ? text
                : null;
    }

    /**
     * Runtime exception used when OpenSearch indexing fails.
     */
    public static class IndexingFailedException
            extends RuntimeException {

        public IndexingFailedException(
                Throwable cause) {

            super(cause);
        }
    }
}