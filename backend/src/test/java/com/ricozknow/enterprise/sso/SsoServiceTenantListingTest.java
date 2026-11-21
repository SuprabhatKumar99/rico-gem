package com.ricozknow.enterprise.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.security.SecretEncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SsoServiceTenantListingTest {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test void adminSsoListingQueriesOnlyCurrentTenant() {
        var repository = mock(SsoConnectionRepository.class);
        var service = new SsoService(repository, mock(AuditService.class),
                mock(SecretEncryptionService.class), new ObjectMapper(),
                mock(SamlRelyingPartyRegistrationRepository.class));
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        SsoConnection expected = new SsoConnection();
        expected.setTenantId(tenantId);
        when(repository.findByTenantId(tenantId)).thenReturn(List.of(expected));
        assertEquals(List.of(expected), service.list());
        verify(repository).findByTenantId(tenantId);
        verify(repository, never()).findAll();
    }
}
