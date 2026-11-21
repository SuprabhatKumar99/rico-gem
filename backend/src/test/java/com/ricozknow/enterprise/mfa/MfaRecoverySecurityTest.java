package com.ricozknow.enterprise.mfa;

import com.ricozknow.audit.AuditService;
import com.ricozknow.auth.RefreshTokenService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.security.SecretEncryptionService;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaRecoverySecurityTest {
    @Mock UserRepository users;
    @Mock MfaRecoveryCodeRepository codes;
    @Mock RefreshTokenService refreshTokens;
    @Mock AuditService audit;
    @Mock MfaAttemptLimiter attempts;
    MfaService service;
    UUID tenant;
    User user;

    @BeforeEach void setup() {
        tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant);
        user.setMfaEnabled(true);
        user.setMfaSecret("JBSWY3DPEHPK3PXP");
        String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        service = new MfaService(users, codes, refreshTokens, audit,
                new SecretEncryptionService("test=" + key, "test"), attempts);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void enablingMfaAgainCannotEraseExistingSecret() {
        String oldSecret = user.getMfaSecret();
        assertThatThrownBy(() -> service.enroll(user)).isInstanceOf(ResponseStatusException.class);
        assertThat(user.getMfaSecret()).isEqualTo(oldSecret);
        verifyNoInteractions(users);
    }

    @Test void recoveryCodeMustBeConsumedAndHasTenantScope() {
        String recovery = "0123456789ABCDEF0123456789ABCDEF";
        when(codes.consume(eq(tenant), eq(user.getId()), anyString())).thenReturn(1, 0);
        assertThat(service.verifyOrConsume(user, recovery)).isTrue();
        assertThat(service.verifyOrConsume(user, recovery)).isFalse();
        verify(codes, times(2)).consume(eq(tenant), eq(user.getId()), anyString());
    }

    @Test void disablingWithoutValidProofPreservesMfa() {
        assertThatThrownBy(() -> service.disable(user, "not-valid"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(user.isMfaEnabled()).isTrue();
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test void throttledMfaAttemptCannotSpendARecoveryCode() {
        when(attempts.isBlocked(tenant, user.getId())).thenReturn(true);
        assertThat(service.verifyOrConsume(user, "0123456789ABCDEF0123456789ABCDEF")).isFalse();
        verifyNoInteractions(codes);
    }

    @Test void invalidProofCannotRegenerateRecoveryCodes() {
        assertThatThrownBy(() -> service.regenerate(user, "bad"))
                .isInstanceOf(ResponseStatusException.class);
        verify(codes, never()).deleteForUser(any(), any());
        verify(refreshTokens, never()).revokeAllForUser(any());
    }

    @Test void foreignTenantAccountRejectedBeforeConsumingCode() {
        TenantContext.set(UUID.randomUUID());
        assertThatThrownBy(() -> service.verifyOrConsume(user, "0123456789ABCDEF0123456789ABCDEF"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(codes);
    }
}
