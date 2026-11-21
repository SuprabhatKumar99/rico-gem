package com.ricozknow.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumes ricozknow:webhook-jobs (pushed by WebhookJobPublisher after an
 * article publish/archive commits) and delivers to every active subscription
 * for that tenant+event type. Same blocking-poll-thread shape as
 * IndexingWorker, for the same reason: a dedicated thread doing a blocking
 * Redis pop is a cleaner fit than a tight scheduled loop.
 *
 * Delivery attempts are capped at 3 tries with a short fixed delay between
 * them (not exponential backoff — this queue isn't meant to hold a job for
 * hours; if 3 quick attempts all fail, the subscriber endpoint is down/wrong,
 * and the delivery log is what an admin checks, not an automatic retry storm
 * hours later). A subscriber wanting stronger guarantees should poll the
 * Agent API as a fallback — webhooks here are a latency optimization over
 * polling, not a replacement for it with delivery guarantees to match.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryWorker {

    private static final String WEBHOOK_QUEUE_KEY = "ricozknow:webhook-jobs";
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    private static final String SIGNATURE_HEADER = "X-RicozKnow-Signature";

    private final StringRedisTemplate redisTemplate;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final com.ricozknow.common.SsrfGuard ssrfGuard;
    private final com.ricozknow.enterprise.webhook.WebhookDlqService dlqService;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ricozknow-webhook-delivery");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private volatile boolean running = false;

    // Wait until all ApplicationRunners (including S3 bucket initialization)
    // have succeeded. No Redis polling thread runs if startup fails.
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (stopped.get() || !started.compareAndSet(false, true)) {
            return;
        }
        running = true;
        executor.submit(this::pollLoop);
    }

    // ContextClosedEvent precedes RedisConnectionFactory destruction.
    @EventListener(ContextClosedEvent.class)
    public void onApplicationClosing() {
        stop();
    }

    @PreDestroy
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        running = false;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(7, TimeUnit.SECONDS)) {
                log.warn("Webhook worker did not terminate within 7 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void pollLoop() {
        log.info("Webhook delivery worker started");
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    String payload = redisTemplate.opsForList()
                            .rightPop(WEBHOOK_QUEUE_KEY, POLL_TIMEOUT);
                    if (payload != null) {
                        handleJob(payload);
                    }
                } catch (Exception ex) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    log.error("Webhook delivery polling failed; retrying after a short delay", ex);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            log.info("Webhook delivery worker stopped");
        }
    }

    private void handleJob(String payload) {
        try {
            WebhookJobPublisher.WebhookJob job = objectMapper.readValue(payload, WebhookJobPublisher.WebhookJob.class);
            List<WebhookSubscription> subscriptions = subscriptionRepository.findByTenantIdAndActiveTrue(job.tenantId());

            for (WebhookSubscription subscription : subscriptions) {
                if (!running || Thread.currentThread().isInterrupted()) {
                    break;
                }
                if (subscribesTo(subscription, job.eventType())) {
                    deliver(subscription, job);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process webhook job: {}", payload, ex);
        }
    }

    private boolean subscribesTo(WebhookSubscription subscription, WebhookEventType eventType) {
        try {
            List<String> types = objectMapper.readerForListOf(String.class).readValue(subscription.getEventTypes());
            return types.contains(eventType.name());
        } catch (Exception ex) {
            return false;
        }
    }

    private void deliver(WebhookSubscription subscription, WebhookJobPublisher.WebhookJob job) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setTenantId(subscription.getTenantId());
        delivery.setSubscriptionId(subscription.getId());
        delivery.setEventType(job.eventType());

        String bodyJson;
        try {
            var envelope = new java.util.LinkedHashMap<String, Object>();
            envelope.put("eventType", job.eventType().name());
            envelope.put("occurredAt", job.enqueuedAt().toString());
            envelope.put("data", job.payload());
            bodyJson = objectMapper.writeValueAsString(envelope);
            delivery.setPayload(bodyJson);
        } catch (Exception ex) {
            log.error("Failed to build webhook payload for subscription {}", subscription.getId(), ex);
            delivery.setPayload("{}");
            delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            delivery.setLastError("Failed to build payload: " + ex.getMessage());
            deliveryRepository.save(delivery);
            return;
        }

        try {
            // Re-check at send time, not just at registration (see SsrfGuard's
            // doc): DNS for the target host can change between the two.
            ssrfGuard.assertSafeTarget(subscription.getUrl());
        } catch (com.ricozknow.common.SsrfGuard.SsrfViolationException ex) {
            log.warn("Blocked webhook delivery for subscription {} as an unsafe target: {}",
                    subscription.getId(), ex.getMessage());
            delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
            delivery.setLastError("Blocked: " + ex.getMessage());
            deliveryRepository.save(delivery);
            return;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            if (!running || Thread.currentThread().isInterrupted()) {
                return;
            }
            delivery.setAttemptCount(attempt);
            try {
                String signature = sign(bodyJson, subscription.getSecret());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(subscription.getUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header(SIGNATURE_HEADER, "sha256=" + signature)
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    delivery.setStatus(WebhookDelivery.DeliveryStatus.SUCCESS);
                    delivery.setDeliveredAt(Instant.now());
                    deliveryRepository.save(delivery);
                    return;
                }
                delivery.setLastError("HTTP " + response.statusCode());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.info("Webhook delivery interrupted for subscription {}", subscription.getId());
                return;
            } catch (Exception ex) {
                delivery.setLastError(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }

            if (attempt < MAX_ATTEMPTS) {
                sleepQuietly(RETRY_DELAY);
            }
        }

        delivery.setStatus(WebhookDelivery.DeliveryStatus.FAILED);
        deliveryRepository.save(delivery);
        try { dlqService.move(delivery); } catch (Exception ex) { log.error("Failed to move webhook delivery {} to DLQ", delivery.getId(), ex); }
        log.warn("Webhook delivery failed after {} attempts: subscription={} url={}",
                MAX_ATTEMPTS, subscription.getId(), subscription.getUrl());
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign webhook payload", ex);
        }
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
