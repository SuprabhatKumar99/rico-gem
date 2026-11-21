package com.ricozknow.audit;

import com.ricozknow.common.TenantOwnedImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends TenantOwnedImmutableEntity {

    @PreUpdate
    @PreRemove
    protected void rejectMutation() {
        throw new IllegalStateException("Audit logs are append-only; use the retention process");
    }

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_state")
    private String oldState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_state")
    private String newState;

    @Column(name="ip_address", length=64) private String ipAddress;
    @Column(name="user_agent", columnDefinition="text") private String userAgent;
    @Column(name="request_id", length=128) private String requestId;
    @Column(length=64) private String source;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="text") private String metadata;
}
