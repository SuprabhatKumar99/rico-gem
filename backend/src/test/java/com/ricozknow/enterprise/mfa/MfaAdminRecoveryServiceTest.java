package com.ricozknow.enterprise.mfa;

import com.ricozknow.audit.AuditService;
import com.ricozknow.audit.CriticalSecurityAuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.role.RoleName;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MfaAdminRecoveryServiceTest {
    private final MfaAdminRecoveryRepository requests = mock(MfaAdminRecoveryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MfaService mfa = mock(MfaService.class);
    private final AuditService audit = mock(AuditService.class);
    private final CriticalSecurityAuditService critical = mock(CriticalSecurityAuditService.class);
    private final MfaAdminRecoveryService service = new MfaAdminRecoveryService(requests, users, mfa, audit, critical);
    private final UUID tenant = UUID.randomUUID();
    private final User requester = admin(tenant);
    private final User approver = admin(tenant);
    private final User target = normal(tenant);

    @BeforeEach void init() {
        TenantContext.set(tenant);
        login(requester);
        when(users.findById(requester.getId())).thenReturn(Optional.of(requester));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); TenantContext.clear(); }

    private static User admin(UUID t) {
        User u = normal(t); u.setRoles(Set.of(new Role(RoleName.ADMIN))); return u;
    }
    private static User normal(UUID t) {
        User u = new User();u.setId(UUID.randomUUID());u.setTenantId(t);
        u.setEmail("test@example.com");u.setStatus(User.UserStatus.ACTIVE);u.setMfaEnabled(true);
        return u;
    }
    private static void login(User u) {
        var p = AuthenticatedUser.from(u);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }
    private MfaAdminRecoveryRequest request(UUID tenant, UUID requestedBy, UUID targetUserId) {
        MfaAdminRecoveryRequest r = new MfaAdminRecoveryRequest();
        r.setId(UUID.randomUUID());r.setTenantId(tenant);
        r.setRequestedBy(requestedBy);r.setTargetUserId(targetUserId);
        r.setExternalTicket("HELP-123456");r.setExpiresAt(Instant.now().plusSeconds(600));
        return r;
    }

    @Test void requesterCannotApproveOwnRecoveryRequest() {
        var r = request(tenant, requester.getId(), target.getId());
        when(requests.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.approve(r.getId())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mfa);
    }

    @Test void foreignTenantRecoveryTargetIsRejected() {
        User foreign = normal(UUID.randomUUID());
        when(users.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.request(foreign.getId(), "HELP-123456", "Verified callback"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(requests, mfa);
    }

    @Test void singleAdminCannotPerformRecovery() {
        assertThatThrownBy(() -> service.request(requester.getId(), "HELP-123456", "Verified callback"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(mfa);
    }

    @Test void dualApprovalDisablesMfaExactlyOnce() {
        var r = request(tenant, requester.getId(), target.getId());
        when(users.findById(approver.getId())).thenReturn(Optional.of(approver));
        when(users.findById(target.getId())).thenReturn(Optional.of(target));
        when(requests.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        login(approver);
        var approved = service.approve(r.getId());
        assertThat(approved.getStatus()).isEqualTo(MfaAdminRecoveryRequest.Status.APPROVED);
        assertThat(approved.getApprovedBy()).isEqualTo(approver.getId());
        verify(mfa, times(1)).resetAfterDualAdminApproval(target);
        verify(critical, times(1)).append(approver.getId(), "MfaAdminRecovery", r.getId(), "MFA_RESET_APPROVED");
        assertThatThrownBy(() -> service.approve(r.getId())).isInstanceOf(ResponseStatusException.class);
        verify(mfa, times(1)).resetAfterDualAdminApproval(target);
    }

    @Test void expiredRequestNeverChangesMfa() {
        var r = request(tenant, requester.getId(), target.getId());
        r.setExpiresAt(Instant.now().minusSeconds(1));
        login(approver);
        when(users.findById(approver.getId())).thenReturn(Optional.of(approver));
        when(requests.findByTenantIdAndId(tenant, r.getId())).thenReturn(Optional.of(r));
        assertThat(service.approve(r.getId()).getStatus()).isEqualTo(MfaAdminRecoveryRequest.Status.EXPIRED);
        verifyNoInteractions(mfa);
    }
}
