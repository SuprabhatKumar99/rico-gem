package com.ricozknow.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.SsrfGuard;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.webhook.dto.CreateWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookSubscriptionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WebhookSubscriptionRepository repository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final SsrfGuard ssrfGuard;

    @Transactional(readOnly = true)
    public List<WebhookSubscription> list() {
        return repository.findByTenantId(TenantContext.get());
    }

    @Transactional
    public WebhookSubscription create(CreateWebhookRequest request) {
        // Registration-time check (see SsrfGuard's doc for why this alone
        // isn't sufficient — WebhookDeliveryWorker re-checks at send time too).
        ssrfGuard.assertSafeTarget(request.url());

        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setUrl(request.url());
        subscription.setSecret(generateSecret());
        subscription.setActive(true);
        subscription.setCreatedBy(currentUserId());
        try {
            subscription.setEventTypes(objectMapper.writeValueAsString(
                    request.eventTypes().stream().map(Enum::name).collect(Collectors.toList())));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize event types", ex);
        }
        subscription = repository.save(subscription);

        auditService.record("WebhookSubscription", subscription.getId(), "WEBHOOK_CREATED", null, request.url());
        return subscription;
    }

    @Transactional
    public void deactivate(UUID id) {
        WebhookSubscription subscription = get(id);
        subscription.setActive(false);
        auditService.record("WebhookSubscription", id, "WEBHOOK_DEACTIVATED", null, null);
    }

    @Transactional
    public void delete(UUID id) {
        WebhookSubscription subscription = get(id);
        repository.delete(subscription);
        auditService.record("WebhookSubscription", id, "WEBHOOK_DELETED", null, subscription.getUrl());
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> deliveryHistory(UUID subscriptionId) {
        get(subscriptionId); // 404s if it doesn't belong to this tenant
        return deliveryRepository.findByTenantIdAndSubscriptionIdOrderByCreatedAtDesc(TenantContext.get(), subscriptionId);
    }

    private WebhookSubscription get(UUID id) {
        return repository.findByTenantIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new IllegalStateException("Webhook subscription not found: " + id));
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return principal.userId();
        }
        throw new IllegalStateException("No authenticated user in context");
    }
}
