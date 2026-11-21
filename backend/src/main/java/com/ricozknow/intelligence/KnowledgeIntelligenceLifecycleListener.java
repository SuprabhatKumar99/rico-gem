package com.ricozknow.intelligence;

import com.ricozknow.article.event.ArticlePublishedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class KnowledgeIntelligenceLifecycleListener {
    private final KnowledgeIntelligenceService intelligence;
    @EventListener
    public void onPublished(ArticlePublishedEvent event) {
        try { intelligence.analyze(event.articleId()); } catch (Exception ignored) { /* indexing must not fail publication */ }
    }
}
