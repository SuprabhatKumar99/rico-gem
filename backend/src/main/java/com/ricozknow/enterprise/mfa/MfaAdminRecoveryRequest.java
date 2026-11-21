package com.ricozknow.enterprise.mfa;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A dual-control recovery request. No authenticator material is ever disclosed to administrators. */
@Entity
@Table(name = "mfa_admin_recovery_requests")
@Getter @Setter @NoArgsConstructor
public class MfaAdminRecoveryRequest extends TenantOwnedEntity {
    @Column(name = "target_user_id", nullable = false, updatable = false)
    private UUID targetUserId;
    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "external_ticket", nullable = false, length = 160, updatable = false)
    private String externalTicket;
    @Column(name = "reason", nullable = false, length = 500, updatable = false)
    private String reason;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING;

    public enum Status { PENDING, APPROVED, EXPIRED }
}
