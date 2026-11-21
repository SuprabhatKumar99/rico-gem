package com.ricozknow.auth;

import com.ricozknow.role.Role;
import com.ricozknow.role.RoleName;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtAuthFilterSecurityTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void disabledUserCannotUsePreviouslyIssuedValidJwt() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtService jwt = mock(JwtService.class);
        UserRepository repository = mock(UserRepository.class);
        when(jwt.parse("jwt")).thenReturn(new JwtService.ParsedToken(userId, tenant, "old@example.com", Set.of("ADMIN")));
        User current = user(tenant, userId, User.UserStatus.DISABLED);
        when(repository.findById(userId)).thenReturn(Optional.of(current));
        JwtAuthFilter filter = new JwtAuthFilter(jwt, repository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("Authorization", "Bearer jwt");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test void currentDatabaseRolesReplaceOutdatedJwtRoles() throws Exception {
        UUID tenant = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtService jwt = mock(JwtService.class);
        UserRepository repository = mock(UserRepository.class);
        when(jwt.parse("jwt")).thenReturn(new JwtService.ParsedToken(userId, tenant, "old@example.com", Set.of("ADMIN")));
        User current = user(tenant, userId, User.UserStatus.ACTIVE);
        current.setRoles(Set.of(new Role(RoleName.REVIEWER)));
        when(repository.findById(userId)).thenReturn(Optional.of(current));
        JwtAuthFilter filter = new JwtAuthFilter(jwt, repository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("Authorization", "Bearer jwt");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isNotNull();
            assertThat(((AuthenticatedUser) authentication.getPrincipal()).roleNames())
                    .containsExactly("REVIEWER");
        });
    }

    @Test void tenantClaimMismatchNeverEstablishesAuthentication() throws Exception {
        UUID tokenTenant = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        JwtService jwt = mock(JwtService.class);
        UserRepository repository = mock(UserRepository.class);
        when(jwt.parse("jwt")).thenReturn(new JwtService.ParsedToken(userId, tokenTenant, "x@example.com", Set.of("ADMIN")));
        when(repository.findById(userId)).thenReturn(Optional.of(user(UUID.randomUUID(), userId, User.UserStatus.ACTIVE)));
        JwtAuthFilter filter = new JwtAuthFilter(jwt, repository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/articles");
        request.addHeader("Authorization", "Bearer jwt");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    private static User user(UUID tenant, UUID id, User.UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenant);
        user.setStatus(status);
        user.setEmail("current@example.com");
        user.setEmailVerified(true);
        return user;
    }
}
