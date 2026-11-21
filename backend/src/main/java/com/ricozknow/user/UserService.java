package com.ricozknow.user;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.role.RoleName;
import com.ricozknow.role.RoleRepository;
import com.ricozknow.user.dto.CreateUserRequest;
import com.ricozknow.user.dto.InviteUserRequest;
import com.ricozknow.user.dto.UpdateUserRequest;
import com.ricozknow.auth.InvitationToken;
import com.ricozknow.auth.InvitationTokenRepository;
import com.ricozknow.auth.TokenService;
import com.ricozknow.auth.RefreshTokenService;
import com.ricozknow.notification.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;

/**
 * Covers both tenant staff accounts (ADMIN/EDITOR/REVIEWER) and the
 * "service account" pattern the Agent API relies on: an AGENT_VIEWER user
 * created here, whose credentials an external CRM/support tool then uses
 * against the normal /api/v1/auth/login endpoint (spec section 19's Agent
 * API deliberately reuses the existing auth model rather than inventing a
 * separate API-key scheme not called for anywhere in the spec).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final com.ricozknow.billing.BillingService billing;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final InvitationTokenRepository invitationTokenRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    @Value("${ricozknow.email.invitation-ttl-hours:72}")
    private long invitationTtlHours;
    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public List<User> list() {
        UUID tenantId = TenantContext.get();
        return userRepository.findAll().stream().filter(u -> tenantId.equals(u.getTenantId())).toList();
    }

    @Transactional
    public User create(CreateUserRequest request) {
        UUID tenantId = TenantContext.get();
        if (userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, request.email()).isPresent()) {
            throw new IllegalStateException("A user with email " + request.email() + " already exists");
        }

        billing.checkCapacity(tenantId, "max_seats");
        User user = new User();
        user.setEmail(request.email());
        user.setName(request.name());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(User.UserStatus.ACTIVE);

        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.roles()) {
            roles.add(roleRepository.findByTenantIdAndName(tenantId, roleName.name())
                    .orElseThrow(() -> new IllegalStateException("Role not seeded for tenant: " + roleName)));
        }
        user.setRoles(roles);

        user = userRepository.save(user);
        auditService.record("User", user.getId(), "USER_CREATED", null, request.email());
        return user;
    }

    @Transactional
    public void disable(UUID userId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getTenantId().equals(TenantContext.get()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        user.setStatus(User.UserStatus.DISABLED);
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record("User", userId, "USER_DISABLED", null, null);
    }

    @Transactional
    public User update(UUID userId, UpdateUserRequest request) {
        UUID tenantId = TenantContext.get();
        User user = userRepository.findById(userId).filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, request.email()).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) throw new IllegalStateException("A user with email " + request.email() + " already exists");
        });
        user.setEmail(request.email().trim().toLowerCase());
        user.setName(request.name().trim());
        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.roles()) {
            roles.add(roleRepository.findByTenantIdAndName(tenantId, roleName.name())
                    .orElseThrow(() -> new IllegalStateException("Role not seeded for tenant: " + roleName)));
        }
        user.setRoles(roles);
        auditService.record("User", userId, "USER_UPDATED", null, request);
        return user;
    }

    @Transactional
    public void enable(UUID userId) {
        User user = userRepository.findById(userId).filter(u -> TenantContext.get().equals(u.getTenantId()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        if(user.getStatus()!=User.UserStatus.ACTIVE) billing.checkCapacity(TenantContext.get(), "max_seats");
        user.setStatus(User.UserStatus.ACTIVE);
        auditService.record("User", userId, "USER_ENABLED", null, null);
    }

    @Transactional
    public void invite(InviteUserRequest request) {
        UUID tenantId = TenantContext.get();
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByTenantIdAndEmailIgnoreCase(tenantId, email).isPresent()) {
            throw new IllegalStateException("A user with email " + email + " already exists");
        }
        billing.checkCapacity(tenantId, "max_seats");
        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setPasswordHash(passwordEncoder.encode(tokenService.generate()));
        user.setStatus(User.UserStatus.PENDING_ACTIVATION);
        user.setEmailVerified(false);
        Set<Role> roles = new HashSet<>();
        for (RoleName roleName : request.roles()) {
            roles.add(roleRepository.findByTenantIdAndName(tenantId, roleName.name())
                    .orElseThrow(() -> new IllegalStateException("Role not seeded for tenant: " + roleName)));
        }
        user.setRoles(roles);
        user = userRepository.save(user);

        String rawToken = tokenService.generate();
        InvitationToken token = new InvitationToken();
        token.setUser(user);
        token.setTokenHash(tokenService.hash(rawToken));
        token.setExpiresAt(Instant.now().plus(invitationTtlHours, ChronoUnit.HOURS));
        invitationTokenRepository.save(token);
        emailService.send(user.getEmail(), "You are invited to RicozKnow",
                "You have been invited to RicozKnow. Complete your account setup here: "
                        + frontendBaseUrl + "/accept-invitation?token=" + rawToken);
        auditService.record("User", user.getId(), "USER_INVITED", null, user.getEmail());
    }

    @Transactional
    public AcceptInvitationResult acceptInvitation(String rawToken, String name, String password) {
        InvitationToken token = invitationTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(InvitationToken::isActive)
                .orElseThrow(() -> new IllegalStateException("Invalid or expired invitation"));
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name.trim());
        user.setStatus(User.UserStatus.ACTIVE);
        user.setEmailVerified(true);
        token.setUsedAt(Instant.now());
        auditService.recordForTenant(user.getTenantId(), "User", user.getId(), "INVITATION_ACCEPTED", null, null);
        return new AcceptInvitationResult(user.getId(), user.getTenantId(), user.getEmail());
    }

    public record AcceptInvitationResult(UUID userId, UUID tenantId, String email) {}

    @Transactional
    public void revokeSessions(UUID userId) {
        User user = userRepository.findById(userId).filter(u -> TenantContext.get().equals(u.getTenantId()))
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.record("User", userId, "SESSIONS_REVOKED", null, null);
    }
}
