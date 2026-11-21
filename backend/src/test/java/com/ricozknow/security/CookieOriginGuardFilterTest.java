package com.ricozknow.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class CookieOriginGuardFilterTest {
    private final CookieOriginGuardFilter filter =
            new CookieOriginGuardFilter("http://wiki.ricozknow.com:4200");

    private MockHttpServletRequest request(String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (origin != null) request.addHeader("Origin", origin);
        return request;
    }

    @Test void rejectsUntrustedRefreshBeforeController() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/auth/refresh", "https://attacker.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void rejectsMissingOriginOrRefererForCookieMutation() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/auth/logout", null), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void allowsOnlyExactTrustedOrigin() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        var request = request("/api/v1/auth/refresh", "http://wiki.ricozknow.com:4200");
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertSame(request, chain.getRequest());
    }

    @Test void protectsRefreshEvenUnderAServletContextPath() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        var request = request("/ricoz/api/v1/auth/refresh", "https://attacker.example");
        request.setContextPath("/ricoz");
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void blocksForeignOriginFromSettingCustomerSession() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/customer/auth/consume", "https://evil.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void blocksForeignOriginFromCustomerLogout() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/customer/auth/logout", "https://evil.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void blocksCrossOriginSavedSearchCreation() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/portal/search/saved", "https://evil.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void blocksCrossOriginSavedSearchDeletion() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        var request = new MockHttpServletRequest("DELETE", "/api/v1/portal/search/saved/123");
        request.addHeader("Origin", "https://evil.example");
        filter.doFilter(request, response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void blocksCrossOriginSocialSessionCompletion() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request("/api/v1/auth/social/finish", "https://evil.example"), response, chain);
        assertEquals(403, response.getStatus());
        assertNull(chain.getRequest());
    }

    @Test void leavesBearerProtectedApiOutsideCookieGuard() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        var request = request("/api/v1/articles", "https://attacker.example");
        filter.doFilter(request, response, chain);
        assertSame(request, chain.getRequest());
    }
}
