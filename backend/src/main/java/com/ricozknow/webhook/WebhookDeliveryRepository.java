package com.ricozknow.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByTenantIdAndSubscriptionIdOrderByCreatedAtDesc(UUID tenantId, UUID subscriptionId);
    List<WebhookDelivery> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, WebhookDelivery.DeliveryStatus status);
}
