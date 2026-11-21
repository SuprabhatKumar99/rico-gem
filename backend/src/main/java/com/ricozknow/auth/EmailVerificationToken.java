package com.ricozknow.auth;

import com.ricozknow.common.TenantOwnedEntity;
import com.ricozknow.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
@Getter @Setter @NoArgsConstructor
public class EmailVerificationToken extends TenantOwnedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isActive() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }
}
