package com.ricozknow.article.dto;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record ArticleMetadataRequest(
    List<@Size(max=80) String> tags,
    List<UUID> relatedArticleIds,
    Boolean pinned,
    Boolean featured
) {}
