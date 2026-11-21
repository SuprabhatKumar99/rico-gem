package com.ricozknow.tenant;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression checks that tenant-scoping is not selected by hostile request data. */
class TenantResolverFilterSecurityTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantResolverFilter filter = new TenantResolverFilter(tenants);

    TenantResolverFilterSecurityTest() {
        ReflectionTestUtils.setField(filter, "baseDomain", "ricozknow.com");
    }

    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test void authenticatedTenantWinsOverHostAndQuery() throws Exception {
        UUID actual = UUID.randomUUID();
        var user = new AuthenticatedUser(UUID.randomUUID(), actual,
                "user@example.test", "unused", Set.of("EDITOR"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        var request = new MockHttpServletRequest("GET", "/api/v1/portal/articles");
        request.setServerName("attacker.ricozknow.com");
        request.setParameter("tenant", "attacker");
        var observed = new AtomicReference<UUID>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> observed.set(TenantContext.get()));
        assertEquals(actual, observed.get());
        assertNull(TenantContext.getOrNull(), "TenantContext must be cleared when request ends");
        verifyNoInteractions(tenants);
    }

    @Test void anonymousRequestCannotSwitchTenantWithQueryOutsidePortal() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.setServerName("evil.example.test");
        request.setParameter("tenant", "victim");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Unresolved tenant must be rejected"));
        assertEquals(400, response.getStatus());
        verify(tenants).findByCustomDomainIgnoreCase("evil.example.test");
        verify(tenants, never()).findBySubdomain("victim");
    }
    @Test void customerAuthenticationCanResolveTenantByExplicitPortalSlug() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        when(tenants.findBySubdomain("wiki")).thenReturn(java.util.Optional.of(tenant));
        var request = new MockHttpServletRequest("POST", "/api/v1/customer/auth/consume");
        request.setServerName("localhost");
        request.setParameter("tenant", "wiki");
        var observed = new AtomicReference<UUID>();
        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> observed.set(TenantContext.get()));
        assertEquals(tenantId, observed.get());
        assertNull(TenantContext.getOrNull());
    }

    @Test void socialOauthCallbackIsTenantIndependentUntilItsStateIsVerified() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/auth/social/google/callback");
        request.setServerName("localhost");
        var called = new java.util.concurrent.atomic.AtomicBoolean(false);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> called.set(true));
        assertTrue(called.get());
        verifyNoInteractions(tenants);
    }

    @Test void unrelatedEndpointsStillIgnoreHostileTenantParameters() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/assets/123");
        request.setServerName("localhost");
        request.setParameter("tenant", "victim");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Tenant should not resolve"));
        assertEquals(400, response.getStatus());
        verify(tenants, never()).findBySubdomain("victim");
    }

}
