package com.ricozknow.customer;

import com.ricozknow.auth.TokenService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.EmailService;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Customer authentication belongs to the tenant resolved by the request, not
 * to an arbitrary tenant supplied in a body, query string, or cookie.
 */
@Service
@RequiredArgsConstructor
public class CustomerAuthService {
    private final CustomerUserRepository users;
    private final CustomerMagicLinkRepository links;
    private final CustomerSessionRepository sessions;
    private final TenantRepository tenants;
    private final TokenService tokens;
    private final EmailService email;

    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}")
    String frontend;
    @Value("${ricozknow.customer.magic-link-ttl-minutes:20}")
    long ttl;

    @Transactional
    public void request(String tenantSlug, String emailAddress) {
        UUID tenantId = TenantContext.get();
        if (tenantSlug == null || tenantSlug.isBlank() || emailAddress == null || emailAddress.isBlank()) {
            throw new IllegalArgumentException("Tenant and email are required");
        }
        Tenant tenant = tenants.findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT))
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE && tenantId.equals(t.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        String normalizedEmail = emailAddress.trim().toLowerCase(Locale.ROOT);
        CustomerUser customer = users.findByTenantIdAndEmailIgnoreCase(tenantId, normalizedEmail)
                .orElseGet(() -> {
                    CustomerUser created = new CustomerUser();
                    created.setTenantId(tenantId);
                    created.setEmail(normalizedEmail);
                    return users.save(created);
                });
        if (!isActive(customer, tenantId)) {
            // Disabled customer accounts must not receive a fresh login token.
            return;
        }
        String rawToken = tokens.generate();
        CustomerMagicLink link = new CustomerMagicLink();
        link.setTenantId(tenantId);
        link.setCustomer(customer);
        link.setTokenHash(tokens.hash(rawToken));
        link.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.MINUTES));
        links.save(link);
        email.send(customer.getEmail(), "Your sign-in link for " + tenant.getName(),
                "Sign in to " + tenant.getName() + ": " + frontend + "/portal?tenant="
                        + tenant.getSlug() + "&magic=" + rawToken
                        + "\n\nThis link expires in " + ttl + " minutes.");
    }

    public record LoginSession(CustomerSession session, String rawToken) {}

    /**
     * Lock the token row until its consumption and session creation commit.
     * Concurrent requests cannot both redeem the same magic link.
     */
    @Transactional
    public LoginSession consume(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Invalid sign-in link");
        UUID tenantId = TenantContext.get();
        CustomerMagicLink link = links.lockByTenantIdAndTokenHash(tenantId, tokens.hash(raw))
                .filter(CustomerMagicLink::active)
                .filter(l -> isActive(l.getCustomer(), tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired sign-in link"));

        link.setUsedAt(Instant.now());
        CustomerUser customer = link.getCustomer();
        customer.setLastLoginAt(Instant.now());

        CustomerSession session = new CustomerSession();
        session.setTenantId(tenantId);
        session.setCustomer(customer);
        String rawSessionToken = tokens.generate();
        session.setTokenHash(tokens.hash(rawSessionToken));
        session.setExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        sessions.save(session);
        return new LoginSession(session, rawSessionToken);
    }

    /** A session from tenant A must NEVER authorize a portal request to tenant B. */
    @Transactional(readOnly = true)
    public CustomerUser current(String raw) {
        if (raw == null || raw.isBlank()) return null;
        UUID tenantId = TenantContext.get();
        return sessions.findByTenantIdAndTokenHash(tenantId, tokens.hash(raw))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(CustomerSession::getCustomer)
                .filter(customer -> isActive(customer, tenantId))
                .orElse(null);
    }

    @Transactional
    public void revoke(String raw) {
        if (raw == null || raw.isBlank()) return;
        sessions.findByTenantIdAndTokenHash(TenantContext.get(), tokens.hash(raw))
                .ifPresent(sessions::delete);
    }

    private static boolean isActive(CustomerUser customer, UUID tenantId) {
        return customer != null && tenantId.equals(customer.getTenantId())
                && "ACTIVE".equalsIgnoreCase(customer.getStatus());
    }
}
