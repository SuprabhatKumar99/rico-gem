package com.ricozknow.auth;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.mfa.MfaService;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import com.ricozknow.auth.dto.LoginResult;
import com.ricozknow.auth.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SocialIdentityService {
    private final JdbcTemplate db;
    private final TenantRepository tenants;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final MfaService mfa;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final AuditService audit;
    private final SocialLoginStateService states;
    private final LoginRateLimiter rateLimiter;

    public record Identity(String issuer, String subject, String email, String name, boolean emailVerified) {}
    public record FinishResult(boolean mfaRequired, LoginResult result) {}

    /** Explicit account linking needs recent credential proof, not only a stolen JWT. */
    @Transactional
    public void authorizeLink(AuthenticatedUser principal, String proof, String ip) {
        TenantContext.set(principal.tenantId());
        try {
            User user = users.findById(principal.userId())
                    .filter(u -> principal.tenantId().equals(u.getTenantId()))
                    .filter(u -> u.getStatus() == User.UserStatus.ACTIVE && u.isEmailVerified())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
            if (proof == null || proof.isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password or MFA proof required");
            if (user.isMfaEnabled()) {
                if (!mfa.verifyOrConsume(user, proof))
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication proof");
            } else {
                Tenant tenant = tenants.findById(principal.tenantId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace unavailable"));
                if (rateLimiter.isBlocked(tenant.getSlug(), user.getEmail(), ip))
                    throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts");
                if (!passwords.matches(proof, user.getPasswordHash())) {
                    rateLimiter.recordFailure(tenant.getSlug(), user.getEmail(), ip);
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication proof");
                }
                rateLimiter.recordSuccess(tenant.getSlug(), user.getEmail(), ip);
            }
        } finally { TenantContext.clear(); }
    }

    @Transactional
    public String resolveAndTicket(SocialLoginStateService.Transaction txn, Identity identity) {
        Tenant tenant = tenants.findBySlug(txn.tenantSlug())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown workspace"));
        TenantContext.set(tenant.getId());
        try {
            // Both the OIDC issuer AND subject are part of the identity (crucial for Entra ID).
            if (identity.issuer() == null || identity.subject() == null || identity.subject().isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unverified social identity");
            List<UUID> linked = db.query("""
                    select user_id from social_identities
                    where tenant_id=? and provider=? and issuer=? and subject=?
                    """, (rs, i) -> (UUID) rs.getObject(1), tenant.getId(), txn.provider(), identity.issuer(), identity.subject());
            User user;
            if (txn.linkUserId() != null) {
                user = users.findById(txn.linkUserId())
                        .filter(u -> tenant.getId().equals(u.getTenantId()) && u.getStatus() == User.UserStatus.ACTIVE && u.isEmailVerified())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Account cannot be linked"));
                if (!linked.isEmpty() && !linked.get(0).equals(user.getId()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Social identity belongs to a different account");
                if (linked.isEmpty()) insertLink(tenant.getId(), user.getId(), txn.provider(), identity);
            } else if (!linked.isEmpty()) {
                user = users.findById(linked.get(0))
                        .filter(u -> tenant.getId().equals(u.getTenantId()))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identity unavailable"));
            } else {
                // Tenant access must be granted by an existing account/invitation.
                // Auto-JIT from a third-party email would let anyone knowing a tenant slug
                // create an internal user, even when the tenant does not permit open signup.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Social identity is not linked; sign in and link this provider in Security settings");
            }
            if (user.getStatus() != User.UserStatus.ACTIVE || !user.isEmailVerified())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is not active");
            audit.record("User", user.getId(), txn.linkUserId() == null ? "SOCIAL_IDENTITY_AUTHENTICATED" : "SOCIAL_IDENTITY_LINKED", null, txn.provider());
            return states.ticket(tenant.getId(), user.getId());
        } finally { TenantContext.clear(); }
    }

    @Transactional
    public FinishResult finish(String ticket, String otp, String remoteIp) {
        // First read (not consume) to show an MFA challenge without issuing credentials.
        SocialLoginStateService.Ticket pending = states.readTicket(ticket);
        TenantContext.set(pending.tenantId());
        try {
            User user = users.findById(pending.userId())
                    .filter(u -> pending.tenantId().equals(u.getTenantId()))
                    .filter(u -> u.getStatus() == User.UserStatus.ACTIVE && u.isEmailVerified())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable"));
            if (user.isMfaEnabled() && (otp == null || otp.isBlank())) return new FinishResult(true, null);
            // Single-use ticket: even incorrect OTP consumes the ticket; restart social sign-in.
            SocialLoginStateService.Ticket consumed = states.consumeTicket(ticket);
            if (!consumed.equals(pending)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired sign-in");
            if (user.isMfaEnabled() && !mfa.verifyOrConsume(user, otp))
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid MFA code; restart social sign-in");
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            String access = jwt.issueAccessToken(principal);
            String refreshToken = refresh.issue(user.getTenantId(), user.getId(), null, "Social login", "OIDC", remoteIp);
            audit.record("User", user.getId(), "SOCIAL_LOGIN_SUCCESS", null, null);
            return new FinishResult(false, new LoginResult(
                    new LoginResponse(access, user.getId(), user.getTenantId(), user.getEmail(), principal.roleNames()), refreshToken));
        } finally { TenantContext.clear(); }
    }

    private void insertLink(UUID tenantId, UUID userId, String provider, Identity identity) {
        db.update("""
                insert into social_identities(tenant_id,user_id,provider,issuer,subject) values(?,?,?,?,?)
                on conflict (tenant_id,provider,issuer,subject) do nothing
                """, tenantId, userId, provider, identity.issuer(), identity.subject());
        // Another account could have linked the same provider identity concurrently.
        UUID actual = db.queryForObject("""
                select user_id from social_identities where tenant_id=? and provider=? and issuer=? and subject=?
                """, UUID.class, tenantId, provider, identity.issuer(), identity.subject());
        if (!userId.equals(actual)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity is already linked");
    }

}
