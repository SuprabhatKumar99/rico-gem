package com.ricozknow.rag;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RagApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String RAG_PATH = "/api/v1/rag/";
    private static final String API_KEY_PREFIX = "rk_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RagApiKeyService service;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(RAG_PATH) && !request.getRequestURI().equals("/mcp");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        String xApiKey = request.getHeader("X-API-Key");

        String rawKey = extractApiKey(authorization, xApiKey);

        /*
         * If there is no RAG API key, simply continue.
         *
         * This allows normal JWT authentication to work for other
         * authentication mechanisms.
         */
        if (rawKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * If the request is already authenticated, don't overwrite it.
         */
        Authentication existing =
                SecurityContextHolder.getContext().getAuthentication();

        if (existing != null && existing.isAuthenticated()) {

            log.debug(
                    "Existing authentication found for RAG request: {}",
                    existing.getClass().getSimpleName()
            );

            filterChain.doFilter(request, response);
            return;
        }

        /*
         * Authenticate the API key.
         */
        RagApiKey key = service.authenticate(rawKey, request.getRemoteAddr());

        if (key == null) {

            log.warn(
                    "Invalid or expired RAG API key. prefix={}",
                    safePrefix(rawKey)
            );

            /*
             * Do not immediately return 401 here.
             *
             * Let Spring Security's authorization layer handle the
             * unauthenticated request.
             */
            filterChain.doFilter(request, response);
            return;
        }

        /*
         * Convert scopes stored in the database:
         *
         * RAG_SEARCH
         *
         * into Spring Security authorities:
         *
         * SCOPE_RAG_SEARCH
         */
        String scopeValue = key.getScopes();
        Set<String> scopes = Arrays.stream(
                scopeValue == null ? new String[0] : scopeValue.split(",")
        )
        .map(String::trim)
        .filter(scope -> !scope.isBlank())
        .collect(Collectors.toSet());

        List<SimpleGrantedAuthority> authorities =
                scopes.stream()
                        .map(scope ->
                                new SimpleGrantedAuthority(
                                        "SCOPE_" + scope
                                )
                        )
                        .toList();

        RagApiPrincipal principal =
                new RagApiPrincipal(
                        key.getId(),
                        key.getTenantId(),
                        scopes
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        log.info(
                "RAG API key authenticated successfully: apiKeyId={}, tenantId={}, authorities={}",
                key.getId(),
                key.getTenantId(),
                authorities
        );

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(
            String authorization,
            String xApiKey
    ) {

        /*
         * Prefer X-API-Key if explicitly supplied.
         */
        if (xApiKey != null
                && xApiKey.startsWith(API_KEY_PREFIX)) {

            return xApiKey.trim();
        }

        /*
         * Support:
         *
         * Authorization: Bearer rk_live_xxxxx
         */
        if (authorization != null
                && authorization.startsWith(BEARER_PREFIX)) {

            String value =
                    authorization.substring(
                            BEARER_PREFIX.length()
                    ).trim();

            if (value.startsWith(API_KEY_PREFIX)) {
                return value;
            }
        }

        return null;
    }

    private String safePrefix(String key) {

        if (key == null || key.isBlank()) {
            return "<none>";
        }

        int length = Math.min(16, key.length());

        return key.substring(0, length);
    }
}
