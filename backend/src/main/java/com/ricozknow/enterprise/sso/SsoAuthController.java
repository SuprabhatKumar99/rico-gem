package com.ricozknow.enterprise.sso;

import com.ricozknow.auth.AuthService;
import com.ricozknow.auth.dto.LoginResponse;
import com.ricozknow.auth.dto.LoginResult;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/sso")
@RequiredArgsConstructor
public class SsoAuthController {
    private final SsoService sso;
    private final TenantRepository tenants;
    private final AuthService auth;
    private final SsoLoginHandoffService handoffs;
    private final OidcLoginStateService oidcStates;

    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}") String frontend;
    @Value("${ricozknow.security.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays;
    @Value("${ricozknow.security.jwt.refresh-cookie-secure}") boolean refreshCookieSecure;
    @Value("${ricozknow.security.jwt.refresh-cookie-same-site:Lax}") String refreshCookieSameSite;

    @GetMapping("/{tenantSlug}/oidc/start")
    public Map<String, String> startOidc(@PathVariable String tenantSlug) {
        var tenant = tenants.findBySlug(tenantSlug.toLowerCase(Locale.ROOT))
                .filter(t -> t.getStatus() == com.ricozknow.tenant.Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        TenantContext.set(tenant.getId());
        try {
            String redirectUri = frontend + "/sso/callback";
            var transaction = oidcStates.create(tenant.getSlug(), redirectUri);
            String authorizationUrl = sso.oidcAuthorizationUrl(redirectUri, transaction);
            return Map.of("authorizationUrl", authorizationUrl, "state", transaction.state());
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/oidc/callback")
    public LoginResponse callbackOidc(@RequestParam String code, @RequestParam String state,
                                       HttpServletRequest request, HttpServletResponse response) {
        var transaction = oidcStates.consume(state);
        var tenant = tenants.findBySlug(transaction.tenantSlug())
                .filter(t -> t.getStatus() == com.ricozknow.tenant.Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        TenantContext.set(tenant.getId());
        try {
            var identity = sso.exchangeOidc(code, transaction);
            LoginResult result = auth.ssoLogin(tenant.getSlug(), identity.get("email"), identity.get("name"), request.getRemoteAddr());
            setRefreshCookie(response, result.refreshToken());
            return result.response();
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/saml/{tenantSlug}/start")
    public void startSaml(@PathVariable String tenantSlug, HttpServletResponse response) throws Exception {
        var tenant = tenants.findBySlug(tenantSlug.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        TenantContext.set(tenant.getId());
        try {
            response.sendRedirect("/saml2/authenticate/" + URLEncoder.encode(tenant.getSlug(), StandardCharsets.UTF_8));
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/saml/exchange")
    public LoginResponse exchangeSaml(@RequestParam String code, HttpServletResponse response) {
        LoginResult result = handoffs.consume(code);
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("rk_refresh_token", token)
                .httpOnly(true).secure(refreshCookieSecure).sameSite(refreshCookieSameSite)
                .path("/api/v1/auth").maxAge(Duration.ofDays(refreshTokenTtlDays)).build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
