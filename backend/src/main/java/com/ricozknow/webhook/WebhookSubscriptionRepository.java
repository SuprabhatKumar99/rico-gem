package com.ricozknow.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {
    List<WebhookSubscription> findByTenantId(UUID tenantId);
    Optional<WebhookSubscription> findByTenantIdAndId(UUID tenantId, UUID id);
    List<WebhookSubscription> findByTenantIdAndActiveTrue(UUID tenantId);
}
