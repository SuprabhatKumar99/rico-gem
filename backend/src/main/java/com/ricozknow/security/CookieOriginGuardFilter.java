package com.ricozknow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Origin/Referer verification on cookie-authenticated refresh/logout and
 * customer login/logout flows.
 * Must remain active even when Spring Security CSRF is disabled for the
 * bearer-token REST APIs. The browser sends Origin on cross-origin POSTs;
 * nonbrowser clients must send an allowlisted Origin or Referer too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class CookieOriginGuardFilter extends OncePerRequestFilter {
    private final TrustedBrowserOrigins trusted;

    public CookieOriginGuardFilter(
            @Value("${RICOZ_COOKIE_TRUSTED_ORIGINS:http://localhost:4200}") String origins) {
        this.trusted = new TrustedBrowserOrigins(origins);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"POST".equalsIgnoreCase(method) && !"DELETE".equalsIgnoreCase(method)) return true;
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath + "/")) {
            path = path.substring(contextPath.length());
        }
        // Saved-search subscriptions use rk_customer_session and must not be
        // mutable by an attacker-controlled cross-site form or fetch.
        if (path.equals("/api/v1/portal/search/saved") && "POST".equalsIgnoreCase(method)) return false;
        if (path.startsWith("/api/v1/portal/search/saved/") && "DELETE".equalsIgnoreCase(method)) return false;
        if (!"POST".equalsIgnoreCase(method)) return true;
        return !path.equals("/api/v1/auth/social/finish")
                && !path.equals("/api/v1/auth/refresh")
                && !path.equals("/api/v1/auth/logout")
                && !path.equals("/api/v1/customer/auth/consume")
                && !path.equals("/api/v1/customer/auth/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!trusted.permits(request.getHeader("Origin"), request.getHeader("Referer"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Untrusted origin for cookie-authenticated request");
            return;
        }
        chain.doFilter(request, response);
    }
}
