package com.ricozknow.enterprise.scim;

import com.ricozknow.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** SCIM bearer credentials establish the tenant; never trust tenant headers or hostnames. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@RequiredArgsConstructor
public class ScimAuthFilter extends OncePerRequestFilter {
    private final ScimService service;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/scim/v2/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= "Bearer ".length()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SCIM authentication required");
            return;
        }
        ScimToken token = service.authenticate(authorization.substring(7));
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "SCIM authentication required");
            return;
        }
        try {
            TenantContext.set(token.getTenantId());
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
