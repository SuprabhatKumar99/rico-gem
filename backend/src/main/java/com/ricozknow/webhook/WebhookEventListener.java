package com.ricozknow.webhook;

import com.ricozknow.article.event.ArticleArchivedEvent;
import com.ricozknow.article.event.ArticlePublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * @TransactionalEventListener(phase = AFTER_COMMIT) specifically: if
 * ArticleService's publish/archive transaction were to roll back after
 * publishing the event (it currently never does mid-method, but this is the
 * correct default regardless), we must never have already told an external
 * CRM about a publish that didn't actually happen. This is the one place in
 * the codebase where "fire the side effect only after commit" actually
 * matters enough to spell out — audit/analytics writes elsewhere use
 * REQUIRES_NEW for a different reason (never rolling back with the caller).
 */
@Component
@RequiredArgsConstructor
public class WebhookEventListener {

    private final WebhookJobPublisher webhookJobPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticlePublished(ArticlePublishedEvent event) {
        webhookJobPublisher.enqueue(event.tenantId(), WebhookEventType.ARTICLE_PUBLISHED, Map.of(
                "articleId", event.articleId().toString(),
                "title", event.title(),
                "slug", event.slug(),
                "versionNumber", event.versionNumber()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArticleArchived(ArticleArchivedEvent event) {
        webhookJobPublisher.enqueue(event.tenantId(), WebhookEventType.ARTICLE_ARCHIVED, Map.of(
                "articleId", event.articleId().toString(),
                "title", event.title(),
                "slug", event.slug()
        ));
    }
}
