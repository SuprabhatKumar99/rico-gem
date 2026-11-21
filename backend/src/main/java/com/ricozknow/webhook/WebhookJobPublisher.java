package com.ricozknow.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookJobPublisher {

    private static final String WEBHOOK_QUEUE_KEY = "ricozknow:webhook-jobs";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(UUID tenantId, WebhookEventType eventType, Map<String, Object> eventPayload) {
        try {
            WebhookJob job = new WebhookJob(tenantId, eventType, eventPayload, Instant.now());
            redisTemplate.opsForList().leftPush(WEBHOOK_QUEUE_KEY, objectMapper.writeValueAsString(job));
        } catch (Exception ex) {
            // Same principle as IndexJobPublisher: the article publish/archive
            // already succeeded and is durable in Postgres by the time this
            // runs (this is only ever called from an AFTER_COMMIT listener) —
            // a webhook enqueue failure must never look like a publish failure.
            log.error("Failed to enqueue webhook job for tenant {} event {}", tenantId, eventType, ex);
        }
    }

    public record WebhookJob(UUID tenantId, WebhookEventType eventType, Map<String, Object> payload, Instant enqueuedAt) {
    }
}
