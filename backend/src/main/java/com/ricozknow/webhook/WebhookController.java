package com.ricozknow.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.webhook.dto.CreateWebhookRequest;
import com.ricozknow.webhook.dto.WebhookDeliveryResponse;
import com.ricozknow.webhook.dto.WebhookSubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN-only management of outbound webhook subscriptions (Phase 9). The
 * signing secret is returned exactly once, in the create response — it's
 * never retrievable again afterward, same principle as an API key: if it's
 * lost, delete and recreate the subscription.
 */
@RestController
@RequestMapping("/api/v1/admin/webhooks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class WebhookController {

    private final WebhookSubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<WebhookSubscriptionResponse> list() {
        return subscriptionService.list().stream()
                .map(s -> WebhookSubscriptionResponse.withoutSecret(s, objectMapper))
                .toList();
    }

    @PostMapping
    public ResponseEntity<WebhookSubscriptionResponse> create(@Valid @RequestBody CreateWebhookRequest request) {
        WebhookSubscription created = subscriptionService.create(request);
        // The only time the secret is ever returned — the frontend must show
        // it once and tell the admin to copy it now.
        return ResponseEntity.ok(WebhookSubscriptionResponse.withSecret(created, objectMapper));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        subscriptionService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subscriptionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deliveries")
    public List<WebhookDeliveryResponse> deliveries(@PathVariable UUID id) {
        return subscriptionService.deliveryHistory(id).stream().map(WebhookDeliveryResponse::from).toList();
    }
}
