package com.ricozknow.article.event;

import java.util.UUID;

/**
 * Published by ArticleService on a successful publish. WebhookEventListener
 * (in the webhook module) is the current consumer, but this is deliberately
 * a generic domain event, not "WebhookArticlePublishedEvent" — ArticleService
 * has no idea webhooks exist, matching spec section 4's module-boundary rule
 * that modules talk through events, not by reaching into each other.
 */
public record ArticlePublishedEvent(UUID tenantId, UUID articleId, String title, String slug, int versionNumber) {
}
