package com.ricozknow.auth;

import com.ricozknow.audit.AuditService;
import com.ricozknow.auth.dto.LoginRequest;
import com.ricozknow.auth.dto.LoginResponse;
import com.ricozknow.auth.dto.LoginResult;
import com.ricozknow.auth.dto.RegisterRequest;
import com.ricozknow.auth.dto.RegisterResponse;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.role.RoleName;
import com.ricozknow.role.RoleRepository;
import com.ricozknow.config.AppMetrics;
import com.ricozknow.enterprise.mfa.MfaService;
import com.ricozknow.notification.EmailService;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j 
@Service
@RequiredArgsConstructor
public class AuthService {

    private final com.ricozknow.billing.BillingService billing;
    private final org.springframework.jdbc.core.JdbcTemplate billingDb;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;
    private final AppMetrics appMetrics;
    private final RoleRepository roleRepository;
    private final MfaService mfaService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenService tokenService;
    private final EmailService emailService;


    @Value("${ricozknow.security.jwt.agent-access-token-ttl-minutes}")
    private long agentAccessTokenTtlMinutes;
 
    @Value("${ricozknow.security.jwt.agent-refresh-token-ttl-days}")
    private long agentRefreshTokenTtlDays;
 

    @Value("${ricozknow.email.verification-ttl-hours:24}")
    private long verificationTtlHours;
    @Value("${ricozknow.email.password-reset-ttl-minutes:30}")
    private long passwordResetTtlMinutes;
    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}") //
    private String frontendBaseUrl;

    // @Transactional
    // public LoginResult login(LoginRequest request, String clientIp) {

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp) { return login(request, clientIp, null, null, null); }

    @Transactional
    public LoginResult login(LoginRequest request, String clientIp, String deviceId, String deviceName, String userAgent) {
        if (rateLimiter.isBlocked(request.tenantSlug(), request.email(), clientIp)) {
            throw new AuthException("Too many login attempts. Try again later.");
        }
 
        Tenant tenant = tenantRepository.findBySlug(request.tenantSlug())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new AuthException("Invalid credentials"));

        TenantContext.set(tenant.getId());
        
        try{
            User user = userRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), request.email())
                    .orElseThrow(() -> recordFailureAndFail(request, clientIp));
    
            if (user.getStatus() != User.UserStatus.ACTIVE) {
                throw new AuthException("Account is not active");
            }
            if (!user.isEmailVerified()) {
                throw new AuthException("Email address is not verified");
            }
    
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                // The Redis account + IP throttles survive rollback of this login transaction.
                // Do not permanently DISABLE accounts after arbitrary bad passwords: attackers
                // could lock out other users by guessing their email addresses.
                throw recordFailureAndFail(request, clientIp);
            }

            if (user.isMfaEnabled() && !mfaService.verifyOrConsume(user, request.otp())) {
                // A correct password with an invalid OTP must still count as a failed login.
                throw recordFailureAndFail(request, clientIp);
            }

            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            rateLimiter.recordSuccess(request.tenantSlug(), request.email(), clientIp);
    
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            boolean agentOnly = isAgentOnly(principal.roleNames());
            String accessToken = agentOnly
                    ? jwtService.issueAccessToken(principal, agentAccessTokenTtlMinutes)
                    : jwtService.issueAccessToken(principal);
            String refreshToken = agentOnly
                    ? refreshTokenService.issue(tenant.getId(), user.getId(), agentRefreshTokenTtlDays, deviceId, deviceName, userAgent, clientIp)
                    : refreshTokenService.issue(tenant.getId(), user.getId(), deviceId, deviceName, userAgent, clientIp);
    
            auditService.record("User", user.getId(), "LOGIN_SUCCESS", null, null);
            appMetrics.recordLoginSuccess();
    
            var response = new LoginResponse(
                    accessToken, user.getId(), tenant.getId(), user.getEmail(), principal.roleNames());
            return new LoginResult(response, refreshToken);

        } finally {
            TenantContext.clear();
        }
    }


    @Transactional
    public LoginResult ssoLogin(String tenantSlug, String email, String name, String clientIp) {
        Tenant tenant = tenantRepository.findBySlug(tenantSlug.trim().toLowerCase())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new AuthException("Unknown tenant"));
        TenantContext.set(tenant.getId());
        try {
            String normalized = email.trim().toLowerCase();
            User user = userRepository.findByTenantIdAndEmailIgnoreCase(tenant.getId(), normalized).orElseGet(() -> {
                billing.checkCapacity(tenant.getId(), "max_seats");
                User u = new User(); u.setTenantId(tenant.getId()); u.setEmail(normalized); u.setName(name == null || name.isBlank() ? normalized : name.trim());
                u.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString())); u.setStatus(User.UserStatus.ACTIVE); u.setEmailVerified(true);
                // JIT SSO accounts must start at least privilege; admin assigns stronger roles explicitly.
                u.getRoles().add(roleRepository.findByTenantIdAndName(tenant.getId(), RoleName.AGENT_VIEWER.name())
                        .orElseThrow(() -> new AuthException("Default SSO role is not configured")));
                return userRepository.save(u);
            });
            if (user.getStatus() != User.UserStatus.ACTIVE) throw new AuthException("Account is not active");
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            String access = jwtService.issueAccessToken(principal);
            String refresh = refreshTokenService.issue(tenant.getId(), user.getId(), null, "SSO", "OIDC/SAML", clientIp);
            auditService.record("User", user.getId(), "SSO_LOGIN_SUCCESS", null, null);
            return new LoginResult(new LoginResponse(access,user.getId(),tenant.getId(),user.getEmail(),principal.roleNames()), refresh);
        } finally { TenantContext.clear(); }
    }

    @Transactional
    public LoginResult refresh(String presentedRefreshToken) {
        // RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(presentedRefreshToken);

        // User user = userRepository.findById(rotation.userId())
        //         .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
        //         .orElseThrow(() -> new AuthException("Account no longer active"));

        RefreshTokenService.ValidatedToken validated = refreshTokenService.validateAndRevoke(presentedRefreshToken);
 
        User user = userRepository.findById(validated.userId())
                .filter(u -> u.getTenantId().equals(validated.tenantId()))
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE && u.isEmailVerified())
                .orElseThrow(() -> new AuthException("Account no longer active"));
        
        AuthenticatedUser principal = AuthenticatedUser.from(user);

        boolean agentOnly = isAgentOnly(principal.roleNames());

        // String accessToken = jwtService.issueAccessToken(principal);

        String accessToken = agentOnly
                ? jwtService.issueAccessToken(principal, agentAccessTokenTtlMinutes)
                : jwtService.issueAccessToken(principal);
        String newRefreshToken = agentOnly
                ? refreshTokenService.issue(validated.tenantId(), validated.userId(), agentRefreshTokenTtlDays)
                : refreshTokenService.issue(validated.tenantId(), validated.userId());

        // var response = new LoginResponse(
        //         accessToken, user.getId(), rotation.tenantId(), user.getEmail(), principal.roleNames());
        // return new LoginResult(response, rotation.newPlaintextToken());
    
         var response = new LoginResponse(
                accessToken, user.getId(), validated.tenantId(), user.getEmail(), principal.roleNames());
        return new LoginResult(response, newRefreshToken);
    
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken != null) {
            refreshTokenService.revoke(presentedRefreshToken);
        }
    }


    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String slug = request.tenantSlug().trim().toLowerCase();
        String subdomain = request.subdomain().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();

        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new AuthException("Tenant slug is already registered");
        }

        if (tenantRepository.findBySubdomain(subdomain).isPresent()) {
            throw new AuthException("Subdomain is already registered");
        }

        Tenant tenant = new Tenant();
        tenant.setName(request.organizationName().trim());
        tenant.setSlug(slug);
        tenant.setSubdomain(subdomain);
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);

        tenant = tenantRepository.saveAndFlush(tenant);
        billingDb.update("insert into saas_subscription(tenant_id,plan_code,status,provider,trial_ends_at) values(?,'GROWTH','TRIALING','NONE',now()+interval '14 days')", tenant.getId());

        TenantContext.set(tenant.getId());

        try {
            Role adminRole = null;

            for (RoleName roleName : RoleName.values()) {
                Role role = roleRepository.save(new Role(roleName));

                if (roleName == RoleName.ADMIN) {
                    adminRole = role;
                }
            }

            if (adminRole == null) {
                throw new AuthException("ADMIN role could not be created");
            }

            User user = new User();
            user.setEmail(email);
            user.setName(request.name().trim());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setStatus(User.UserStatus.PENDING_ACTIVATION);
            user.setEmailVerified(false);
            user.setFailedLoginAttempts(0);
            user.getRoles().add(adminRole);

            user = userRepository.save(user);

            auditService.record(
                    "User",
                    user.getId(),
                    "USER_REGISTERED",
                    null,
                    user
            );

            auditService.record(
                    "Tenant",
                    tenant.getId(),
                    "TENANT_REGISTERED",
                    null,
                    tenant
            );

            // sendVerificationEmail(user);

            try {
                sendVerificationEmail(user);
            } catch (Exception ex) {
                log.error(
                        "Registration succeeded but verification email could not be sent for user={}",
                        user.getId(),
                        ex
                );
            }



            return new RegisterResponse(
                    tenant.getId(),
                    user.getId(),
                    tenant.getSlug(),
                    user.getEmail(),
                    RoleName.ADMIN.name(),
                    "Registration successful. Check your email to verify your account."
            );

        } finally {
            TenantContext.clear();
        }
    }

    // private AuthException recordFailureAndFail(LoginRequest request, String clientIp) {
    //     rateLimiter.recordFailure(request.tenantSlug(), request.email(), clientIp);
    //     appMetrics.recordLoginFailure();
    //     return new AuthException("Invalid credentials");
    // }


    /**
     * A "pure" AGENT_VIEWER service account (no staff roles alongside it) is
     * treated as a machine client and gets the shorter TTL class — see the
     * README for the rationale (smaller blast radius if a token leaks from a
     * CRM integration). A human who happens to also hold AGENT_VIEWER gets
     * the normal staff TTLs, since they're a person logging in, not a bot.
     */
    public static boolean isAgentOnly(Set<String> roles) {
        return roles.contains("AGENT_VIEWER")
                && !roles.contains("ADMIN") && !roles.contains("EDITOR") && !roles.contains("REVIEWER");
    }
 
    private AuthException recordFailureAndFail(LoginRequest request, String clientIp) {
        rateLimiter.recordFailure(request.tenantSlug(), request.email(), clientIp);
        appMetrics.recordLoginFailure();
        return new AuthException("Invalid credentials");
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(EmailVerificationToken::isActive)
                .orElseThrow(() -> new AuthException("Invalid or expired verification token"));
        User user = token.getUser();
        user.setEmailVerified(true);
        user.setStatus(User.UserStatus.ACTIVE);
        token.setUsedAt(Instant.now());
        auditService.recordForTenant(user.getTenantId(), "User", user.getId(), "EMAIL_VERIFIED", null, null);
    }

    @Transactional
    public void resendVerification(String tenantSlug, String email) {
        tenantRepository.findBySlug(tenantSlug.trim().toLowerCase())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .flatMap(t -> userRepository.findByTenantIdAndEmailIgnoreCase(t.getId(), email.trim().toLowerCase()))
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    private void sendVerificationEmail(User user) {
        emailVerificationTokenRepository.findByTenantIdAndUserId(user.getTenantId(), user.getId()).stream()
                .filter(EmailVerificationToken::isActive)
                .forEach(t -> t.setUsedAt(Instant.now()));
        String rawToken = tokenService.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setTenantId(user.getTenantId());
        token.setUser(user);
        token.setTokenHash(tokenService.hash(rawToken));
        token.setExpiresAt(Instant.now().plus(verificationTtlHours, ChronoUnit.HOURS));
        emailVerificationTokenRepository.save(token);
        emailService.send(user.getEmail(), "Verify your RicozKnow email",
                "Verify your RicozKnow account here: " + frontendBaseUrl + "/verify-email?token=" + rawToken);
    }

    @Transactional
    public void requestPasswordReset(String tenantSlug, String email) {
        tenantRepository.findBySlug(tenantSlug.trim().toLowerCase())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .flatMap(t -> userRepository.findByTenantIdAndEmailIgnoreCase(t.getId(), email.trim().toLowerCase()))
                .ifPresent(user -> {
                    passwordResetTokenRepository.findByTenantIdAndUserId(user.getTenantId(), user.getId()).stream()
                            .filter(PasswordResetToken::isActive)
                            .forEach(t -> t.setUsedAt(Instant.now()));
                    String rawToken = tokenService.generate();
                    PasswordResetToken token = new PasswordResetToken();
                    token.setTenantId(user.getTenantId());
                    token.setUser(user);
                    token.setTokenHash(tokenService.hash(rawToken));
                    token.setExpiresAt(Instant.now().plus(passwordResetTtlMinutes, ChronoUnit.MINUTES));
                    passwordResetTokenRepository.save(token);
                    emailService.send(user.getEmail(), "Reset your RicozKnow password",
                            "Reset your RicozKnow password here: " + frontendBaseUrl + "/reset-password?token=" + rawToken);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenService.hash(rawToken))
                .filter(PasswordResetToken::isActive)
                .orElseThrow(() -> new AuthException("Invalid or expired password reset token"));
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (user.getStatus() == User.UserStatus.DISABLED) user.setStatus(User.UserStatus.ACTIVE);
        token.setUsedAt(Instant.now());
        refreshTokenService.revokeAllForUser(user.getId());
        auditService.recordForTenant(user.getTenantId(), "User", user.getId(), "PASSWORD_RESET", null, null);
    }
}