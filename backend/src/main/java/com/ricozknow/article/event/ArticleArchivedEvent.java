package com.ricozknow.article.event;

import java.util.UUID;

public record ArticleArchivedEvent(UUID tenantId, UUID articleId, String title, String slug) {
}
