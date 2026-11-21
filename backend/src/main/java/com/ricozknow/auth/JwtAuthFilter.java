package com.ricozknow.auth;

import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT authentication. The signed token identifies the user and tenant;
 * the current user record remains authoritative for account status and roles.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RAG_PATH = "/api/v1/rag/";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith(RAG_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                JwtService.ParsedToken token = jwtService.parse(header.substring(BEARER_PREFIX.length()));
                // Never grant previously issued ADMIN/editor authorities to a
                // deactivated, moved, or demoted user based on stale JWT claims.
                User user = userRepository.findById(token.userId())
                        .filter(u -> u.getTenantId().equals(token.tenantId()))
                        .filter(u -> u.getStatus() == User.UserStatus.ACTIVE && u.isEmailVerified())
                        .orElseThrow(() -> new IllegalArgumentException("Inactive or invalid account"));
                AuthenticatedUser principal = AuthenticatedUser.from(user);
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            } catch (Exception ex) {
                // Invalid token, inactive user or datastore failure: fail closed.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
