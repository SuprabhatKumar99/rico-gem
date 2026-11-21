package com.ricozknow.webhook;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "webhook_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class WebhookSubscription extends TenantOwnedEntity {

    @Column(nullable = false)
    private String url;

    /** HMAC signing secret. Returned to the caller once, at creation — never again. */
    @Column(nullable = false)
    private String secret;

    /** JSON array of WebhookEventType names. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_types", nullable = false)
    private String eventTypes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
}
