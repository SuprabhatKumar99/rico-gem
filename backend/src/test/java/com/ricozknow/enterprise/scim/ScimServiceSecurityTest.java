package com.ricozknow.enterprise.scim;

import com.ricozknow.audit.AuditService;
import com.ricozknow.auth.RefreshTokenService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.role.RoleName;
import com.ricozknow.role.RoleRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScimServiceSecurityTest {
    @Mock com.ricozknow.billing.BillingService billing;
    @Mock ScimTokenRepository tokens;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock AuditService audit;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;

    private ScimService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach void init() {
        TenantContext.set(tenant);
        service = new ScimService(billing, tokens, users, roles, audit, encoder, refreshTokens);
    }
    @AfterEach void resetTenant() { TenantContext.clear(); }

    @Test void foreignTenantUserIdIsNeverWritableOrReadable() {
        UUID id = UUID.randomUUID();
        User foreign = user(UUID.randomUUID(), id, "other@example.com");
        when(users.findById(id)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.replace(id, "victim@example.com", "Victim", false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.deactivate(id)).isInstanceOf(ResponseStatusException.class);
        verify(refreshTokens, never()).revokeAllForUser(any());
        verify(users, never()).save(any());
    }

    @Test void scimCreateNeverTakesOverAnExistingAdminAccount() {
        when(users.findByTenantIdAndEmailIgnoreCase(tenant, "admin@example.com"))
                .thenReturn(Optional.of(user(tenant, UUID.randomUUID(), "admin@example.com")));
        assertThatThrownBy(() -> service.create("ADMIN@example.com", "Fake", false))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(users, never()).save(any());
    }

    @Test void creatingUserAssignsOnlyLeastPrivilegeRole() {
        Role viewer = new Role(RoleName.AGENT_VIEWER);
        when(roles.findByTenantIdAndName(tenant, RoleName.AGENT_VIEWER.name()))
                .thenReturn(Optional.of(viewer));
        when(encoder.encode(any())).thenReturn("random-hash");
        when(users.save(any())).thenAnswer(call -> {
            User u = call.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        User result = service.create(" NEW@example.com ", "New", true);
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getRoles()).containsExactly(viewer);
        assertThat(result.isEmailVerified()).isTrue();
        assertThat(result.getTenantId()).isEqualTo(tenant);
    }

    @Test void patchActuallyDeactivatesAndRevokesSessions() {
        UUID id = UUID.randomUUID();
        User u = user(tenant, id, "member@example.com");
        when(users.findById(id)).thenReturn(Optional.of(u));
        service.patch(id, java.util.List.of(new ScimService.Change("active", false)));
        assertThat(u.getStatus()).isEqualTo(User.UserStatus.DISABLED);
        verify(refreshTokens).revokeAllForUser(id);
    }

    @Test void patchRejectsUnrecognizedFieldsRatherThanSilentlySucceeding() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user(tenant, id, "member@example.com")));
        assertThatThrownBy(() -> service.patch(id, java.util.List.of(new ScimService.Change("roles", Set.of("ADMIN")))))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static User user(UUID tenant, UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setTenantId(tenant);
        u.setEmail(email);
        u.setName("Person");
        u.setStatus(User.UserStatus.ACTIVE);
        return u;
    }
}
