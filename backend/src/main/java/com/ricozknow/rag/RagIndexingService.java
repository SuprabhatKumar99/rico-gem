package com.ricozknow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleVersion;
import com.ricozknow.article.ArticleVersionRepository;
import com.ricozknow.article.VersionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagIndexingService {

    private final OpenSearchClient client;
    private final ArticleRepository articleRepository;
    private final ArticleVersionRepository versionRepository;
    private final EmbeddingService embeddingService;

    @Value("${ricozknow.security.rag.chunk.max-chars:1800}")
    private int maxChars;

    @Value("${ricozknow.security.rag.enabled:false}")
    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean indexVersion(UUID tenantId, UUID articleId, UUID versionId) {

        if (!enabled) {
            return false;
        }

        Article article = articleRepository
                .findByTenantIdAndId(tenantId, articleId)
                .orElse(null);

        ArticleVersion version = versionRepository
                .findByTenantIdAndId(tenantId, versionId)
                .orElse(null);

        if (article == null || version == null) {
            return false;
        }

        if (version.getStatus() != VersionStatus.PUBLISHED
                || article.getActiveVersion() == null
                || !version.getId().equals(article.getActiveVersion().getId())) {
            return false;
        }

        removeArticle(tenantId, articleId);

        List<String> chunks = chunkContent(version.getContent());

        for (int i = 0; i < chunks.size(); i++) {

            String content = chunks.get(i);

            if (content.isBlank()) {
                continue;
            }

            try {
                List<Float> vector = embeddingService.embed(content);

                RagChunkDocument doc = new RagChunkDocument(
                        tenantId.toString(),
                        articleId.toString(),
                        versionId.toString(),
                        articleId + ":" + versionId + ":" + i,
                        article.getSlug(),
                        article.getTitle(),
                        content,
                        article.getCategory() != null
                                ? article.getCategory().getId().toString()
                                : null,
                        article.getCategory() != null
                                ? article.getCategory().getName()
                                : null,
                        article.getVisibility().name(),
                        version.getPublishedAt() != null
                                ? version.getPublishedAt().toString()
                                : null,
                        i,
                        vector,
                        null,
                        null,
                        null,
                        article.getLanguage()
                );

                IndexRequest<RagChunkDocument> request = IndexRequest.of(
                        builder -> builder
                                .index(RagIndexInitializer.INDEX_NAME)
                                .id(tenantId + ":" + doc.chunkId())
                                .document(doc)
                );

                client.index(request);

            } catch (IOException ex) {
                log.error(
                        "Failed to index RAG chunk {} for article {}",
                        i,
                        articleId,
                        ex
                );

                throw new IllegalStateException(
                        "Failed to index RAG chunk",
                        ex
                );
            }
        }

        log.info(
                "Indexed {} RAG chunks for article {} version {}",
                chunks.size(),
                articleId,
                versionId
        );
        return true;
    }

    public boolean removeArticle(UUID tenantId, UUID articleId) {

        if (!enabled) {
            return false;
        }

        try {

            DeleteByQueryRequest request = DeleteByQueryRequest.of(
                    builder -> builder
                            .index(RagIndexInitializer.INDEX_NAME)
                            .query(query -> query.bool(bool -> bool
                                    .filter(filter -> filter.term(term -> term
                                            .field("tenantId")
                                            .value(FieldValue.of(tenantId.toString()))
                                    ))
                                    .filter(filter -> filter.term(term -> term
                                            .field("articleId")
                                            .value(FieldValue.of(articleId.toString()))
                                    ))
                            ))
            );

            client.deleteByQuery(request);
            return true;

        } catch (IOException ex) {

            log.error(
                    "Failed to remove RAG chunks for article {}",
                    articleId,
                    ex
            );

            throw new IllegalStateException(
                    "Failed to remove RAG chunks",
                    ex
            );
        }
    }

    private List<String> chunkContent(JsonNode content) {

        List<String> blocks = new ArrayList<>();

        forEachBlock(content, block -> {

            String type = textOrNull(block, "type");
            String text = textOrNull(block, "text");

            if (text != null
                    && !text.isBlank()
                    && ("heading".equals(type)
                    || "paragraph".equals(type)
                    || "code".equals(type))) {

                blocks.add(text.trim());
            }
        });

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String block : blocks) {

            /*
             * If the individual block itself is larger than maxChars,
             * flush the current chunk first and then split the block.
             */
            if (block.length() > maxChars) {

                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }

                for (int start = 0; start < block.length(); start += maxChars) {

                    int end = Math.min(
                            start + maxChars,
                            block.length()
                    );

                    String part = block.substring(start, end).trim();

                    if (!part.isBlank()) {
                        chunks.add(part);
                    }
                }

                continue;
            }

            /*
             * Current chunk would exceed maxChars.
             */
            if (current.length() > 0
                    && current.length() + block.length() + 2 > maxChars) {

                chunks.add(current.toString().trim());
                current.setLength(0);
            }

            if (current.length() > 0) {
                current.append("\n\n");
            }

            current.append(block);
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    private void forEachBlock(
            JsonNode content,
            Consumer<JsonNode> consumer
    ) {

        if (content == null || !content.isObject()) {
            return;
        }

        JsonNode blocks = content.get("blocks");

        if (blocks != null && blocks.isArray()) {
            blocks.forEach(consumer);
        }
    }

    private String textOrNull(
            JsonNode node,
            String field
    ) {

        JsonNode value = node.get(field);

        return value != null && value.isValueNode()
                ? value.asText()
                : null;
    }
}