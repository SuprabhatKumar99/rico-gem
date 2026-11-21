package com.ricozknow.enterprise.mfa;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "mfa_recovery_codes")
@Getter @Setter
public class MfaRecoveryCode extends TenantOwnedEntity {
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;
}
