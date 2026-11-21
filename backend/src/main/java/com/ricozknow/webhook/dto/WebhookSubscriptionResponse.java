package com.ricozknow.webhook.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.webhook.WebhookSubscription;

import java.util.List;
import java.util.UUID;

/** secret is non-null ONLY in the response returned from the create call — see WebhookController. */
public record WebhookSubscriptionResponse(
        UUID id,
        String url,
        List<String> eventTypes,
        boolean active,
        String secret
) {
    public static WebhookSubscriptionResponse withoutSecret(WebhookSubscription entity, ObjectMapper mapper) {
        return from(entity, mapper, null);
    }

    public static WebhookSubscriptionResponse withSecret(WebhookSubscription entity, ObjectMapper mapper) {
        return from(entity, mapper, entity.getSecret());
    }

    private static WebhookSubscriptionResponse from(WebhookSubscription entity, ObjectMapper mapper, String secret) {
        try {
            List<String> types = mapper.readerForListOf(String.class).readValue(entity.getEventTypes());
            return new WebhookSubscriptionResponse(entity.getId(), entity.getUrl(), types, entity.isActive(), secret);
        } catch (Exception ex) {
            throw new IllegalStateException("Corrupt webhook event-types data for " + entity.getId(), ex);
        }
    }
}
