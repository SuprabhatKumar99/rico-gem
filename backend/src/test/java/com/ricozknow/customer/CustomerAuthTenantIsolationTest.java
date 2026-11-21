package com.ricozknow.customer;

import com.ricozknow.auth.TokenService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.EmailService;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAuthTenantIsolationTest {
    private final CustomerUserRepository users = mock(CustomerUserRepository.class);
    private final CustomerMagicLinkRepository links = mock(CustomerMagicLinkRepository.class);
    private final CustomerSessionRepository sessions = mock(CustomerSessionRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final EmailService email = mock(EmailService.class);
    private final CustomerAuthService service = new CustomerAuthService(users, links, sessions, tenants, tokens, email);

    @AfterEach void cleanup() { TenantContext.clear(); }

    @Test void aForeignTenantSessionCannotUnlockAuthenticatedPortalArticles() {
        UUID currentTenant = UUID.randomUUID();
        TenantContext.set(currentTenant);
        when(tokens.hash("stolen-cookie")).thenReturn("hash");

        // Even a valid token does not matter if the session belongs to another tenant.
        assertNull(service.current("stolen-cookie"));
        verify(sessions).findByTenantIdAndTokenHash(currentTenant, "hash");
        verify(sessions, never()).findByTokenHash(anyString());
    }

    @Test void disabledOrCrossTenantCustomerIsNotAuthenticated() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(tokens.hash("cookie")).thenReturn("hash");
        CustomerSession session = new CustomerSession();
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        CustomerUser otherTenantCustomer = customer(UUID.randomUUID(), "ACTIVE");
        session.setCustomer(otherTenantCustomer);
        when(sessions.findByTenantIdAndTokenHash(tenant, "hash")).thenReturn(Optional.of(session));
        assertNull(service.current("cookie"));
        session.setCustomer(customer(tenant, "DISABLED"));
        assertNull(service.current("cookie"));
        session.setCustomer(customer(tenant, "ACTIVE"));
        assertNotNull(service.current("cookie"));
    }

    @Test void magicLinkIssuedForAnotherTenantIsRejectedBeforeRedemption() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(tokens.hash("magic")).thenReturn("hash");
        assertThrows(IllegalArgumentException.class, () -> service.consume("magic"));
        verify(links).lockByTenantIdAndTokenHash(tenant, "hash");
        verify(links, never()).findByTokenHash(anyString());
        verify(sessions, never()).save(any());
    }

    @Test void validSameTenantMagicLinkCreatesOnlyOneScopedSession() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(tokens.hash("magic")).thenReturn("hash");
        when(tokens.generate()).thenReturn("new-session");
        when(tokens.hash("new-session")).thenReturn("session-hash");
        CustomerMagicLink link = new CustomerMagicLink();
        link.setTenantId(tenant);
        link.setExpiresAt(Instant.now().plusSeconds(120));
        link.setCustomer(customer(tenant, "ACTIVE"));
        when(links.lockByTenantIdAndTokenHash(tenant, "hash")).thenReturn(Optional.of(link));

        var issued = service.consume("magic");
        assertEquals("new-session", issued.rawToken());
        assertEquals(tenant, issued.session().getTenantId());
        assertNotNull(link.getUsedAt());
        verify(sessions, times(1)).save(issued.session());
        assertThrows(IllegalArgumentException.class, () -> service.consume("magic"));
        verify(sessions, times(1)).save(any());
    }

    @Test void alreadyUsedMagicLinkCannotBeRedeemedTwice() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(tokens.hash("magic")).thenReturn("hash");
        CustomerMagicLink used = new CustomerMagicLink();
        used.setTenantId(tenant);
        used.setUsedAt(Instant.now());
        used.setExpiresAt(Instant.now().plusSeconds(120));
        used.setCustomer(customer(tenant, "ACTIVE"));
        when(links.lockByTenantIdAndTokenHash(tenant, "hash")).thenReturn(Optional.of(used));
        assertThrows(IllegalArgumentException.class, () -> service.consume("magic"));
        verify(sessions, never()).save(any());
    }

    @Test void cannotRequestAnotherTenantsMagicLinkBySupplyingItsSlug() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        Tenant foreign = new Tenant();
        foreign.setId(UUID.randomUUID());
        foreign.setStatus(Tenant.TenantStatus.ACTIVE);
        when(tenants.findBySlug("foreign")).thenReturn(Optional.of(foreign));
        assertThrows(IllegalArgumentException.class,
                () -> service.request("foreign", "user@example.com"));
        verify(users, never()).save(any());
        verify(links, never()).save(any());
        verifyNoInteractions(email);
        assertEquals(tenant, TenantContext.get(), "Auth code must not overwrite the request's tenant context");
    }

    @Test void logoutCanRevokeOnlyCurrentTenantSession() {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        when(tokens.hash("cookie")).thenReturn("hash");
        service.revoke("cookie");
        verify(sessions).findByTenantIdAndTokenHash(tenant, "hash");
        verify(sessions, never()).findByTokenHash(anyString());
    }

    private CustomerUser customer(UUID tenant, String status) {
        CustomerUser u = new CustomerUser();
        u.setTenantId(tenant);
        u.setStatus(status);
        return u;
    }
}
