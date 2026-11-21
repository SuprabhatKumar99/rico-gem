package com.ricozknow.enterprise.session;

import com.ricozknow.auth.RefreshTokenRepository;
import com.ricozknow.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionServiceIsolationTest {
    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void listingNeverScansAnotherTenantsTokens() {
        var repository = mock(RefreshTokenRepository.class);
        var service = new SessionService(repository);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repository.findByTenantIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(eq(tenant), eq(user), any(Instant.class)))
                .thenReturn(List.of());
        assertTrue(service.list(user).isEmpty());
        verify(repository, never()).findAll();
    }

    @Test void foreignSessionIdCannotBeRevoked() {
        var repository = mock(RefreshTokenRepository.class);
        var service = new SessionService(repository);
        UUID tenant = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        TenantContext.set(tenant);
        when(repository.findByTenantIdAndUserIdAndId(tenant, user, id)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.revoke(user, id));
        verify(repository, never()).findById(id);
        verify(repository, never()).save(any());
    }
}
