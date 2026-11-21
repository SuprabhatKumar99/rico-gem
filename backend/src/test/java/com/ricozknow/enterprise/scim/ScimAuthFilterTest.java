package com.ricozknow.enterprise.scim;

import com.ricozknow.common.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ScimAuthFilterTest {
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void missingTokenIsRejectedBeforeController() throws Exception {
        ScimService service = mock(ScimService.class);
        ScimAuthFilter filter = new ScimAuthFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(reached.get()).isFalse();
    }

    @Test void validScimTokenSetsOnlyItsOwnTenantAndClearsContext() throws Exception {
        ScimService service = mock(ScimService.class);
        UUID tenant = UUID.randomUUID();
        ScimToken token = new ScimToken();
        token.setTenantId(tenant);
        when(service.authenticate("secret")).thenReturn(token);
        ScimAuthFilter filter = new ScimAuthFilter(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/scim/v2/Users");
        request.addHeader("Authorization", "Bearer secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(TenantContext.get()).isEqualTo(tenant));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThatThrownTenantIsCleared();
    }

    private static void assertThatThrownTenantIsCleared() {
        org.assertj.core.api.Assertions.assertThatThrownBy(TenantContext::get).isInstanceOf(RuntimeException.class);
    }
}
