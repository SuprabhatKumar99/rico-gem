package com.ricozknow.rag;

import java.util.List;

public record RagChunkDocument(
        String tenantId, String articleId, String versionId, String chunkId, String slug, String title, String content,
        String categoryId, String categoryName, String visibility, String publishedAt, int chunkIndex, List<Float> embedding,
        String sourceId, String sourceType, String sourceUrl, String language
) {}
