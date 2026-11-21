package com.ricozknow.webhook.dto;

import com.ricozknow.webhook.WebhookDelivery;

import java.time.Instant;
import java.util.UUID;

public record WebhookDeliveryResponse(
        UUID id,
        String eventType,
        String status,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant deliveredAt
) {
    public static WebhookDeliveryResponse from(WebhookDelivery d) {
        return new WebhookDeliveryResponse(d.getId(), d.getEventType().name(), d.getStatus().name(),
                d.getAttemptCount(), d.getLastError(), d.getCreatedAt(), d.getDeliveredAt());
    }
}
