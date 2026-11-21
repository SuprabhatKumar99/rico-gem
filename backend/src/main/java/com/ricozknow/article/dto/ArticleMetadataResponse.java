package com.ricozknow.article.dto;
import java.util.List;
import java.util.UUID;
public record ArticleMetadataResponse(List<String> tags, List<UUID> relatedArticleIds, boolean pinned, boolean featured) {}
