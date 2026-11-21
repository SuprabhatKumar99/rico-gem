package com.ricozknow.audit;

import com.ricozknow.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CriticalSecurityAuditServiceTest {
    private final AuditLogRepository logs = mock(AuditLogRepository.class);
    private final CriticalSecurityAuditService service = new CriticalSecurityAuditService(logs);
    @AfterEach void clear() { TenantContext.clear(); }
    @Test void failureSavingAuditIsNotSwallowed() {
        UUID tenant = UUID.randomUUID(); TenantContext.set(tenant);
        when(logs.saveAndFlush(any(AuditLog.class))).thenThrow(new IllegalStateException("DB audit unavailable"));
        assertThatThrownBy(() -> service.append(UUID.randomUUID(), "User", UUID.randomUUID(), "MFA_RESET"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("audit unavailable");
    }
}
