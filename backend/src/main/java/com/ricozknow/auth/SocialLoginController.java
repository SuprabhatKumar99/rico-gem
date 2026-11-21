package com.ricozknow.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.auth.dto.LoginResponse;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import com.ricozknow.user.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Public Google/Microsoft login and separately authenticated explicit account linking. */
@RestController
@RequestMapping("/api/v1/auth/social")
@RequiredArgsConstructor
public class SocialLoginController {
    private final SocialLoginStateService states;
    private final SocialIdentityService identities;
    private final TenantRepository tenants;
    private final ObjectMapper mapper;
    private final RestClient http = RestClient.builder().build();

    @Value("${ricozknow.social.google.client-id:}") String googleId;
    @Value("${ricozknow.social.google.client-secret:}") String googleSecret;
    @Value("${ricozknow.social.microsoft.client-id:}") String microsoftId;
    @Value("${ricozknow.social.microsoft.client-secret:}") String microsoftSecret;
    @Value("${ricozknow.social.callback-base-url:http://localhost:8080}") String callbackBase;
    @Value("${ricozknow.email.frontend-base-url:http://localhost:4200}") String frontend;
    @Value("${ricozknow.security.jwt.refresh-token-ttl-days}") long refreshDays;
    @Value("${ricozknow.security.jwt.refresh-cookie-secure}") boolean cookieSecure;
    @Value("${ricozknow.security.jwt.refresh-cookie-same-site:Lax}") String cookieSameSite;

    public record StartResponse(String authorizationUrl, String state) {}
    public record FinishRequest(String ticket, String otp) {}
    public record LinkProof(String proof) {}
    public record FinishResponse(boolean mfaRequired, LoginResponse session) {}
    private record Config(String clientId, String clientSecret, String authorize, String token, String jwks, String issuer) {}

    @GetMapping("/{provider}/start")
    public StartResponse start(@PathVariable String provider, @RequestParam String tenantSlug) {
        return begin(provider, tenantSlug, null);
    }

    @PostMapping("/{provider}/link/start")
    public StartResponse startLink(@PathVariable String provider, @RequestBody LinkProof body,
                                   @AuthenticationPrincipal AuthenticatedUser principal,
                                   HttpServletRequest request) {
        if (principal == null || !principal.enabled())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        Tenant tenant = tenants.findById(principal.tenantId())
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace unavailable"));
        identities.authorizeLink(principal, body == null ? null : body.proof(), request.getRemoteAddr());
        return begin(provider, tenant.getSlug(), principal.userId());
    }

    private StartResponse begin(String provider, String tenantSlug, UUID linkUserId) {
        Config c = config(provider);
        if (tenantSlug == null || !tenantSlug.trim().matches("[a-zA-Z0-9-]{3,100}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid tenant slug");
        Tenant tenant = tenants.findBySlug(tenantSlug.trim().toLowerCase(Locale.ROOT))
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace unavailable"));
        var txn = states.start(tenant.getSlug(), provider, linkUserId);
        String url = c.authorize() + "?response_type=code&client_id=" + enc(c.clientId())
                + "&redirect_uri=" + enc(redirectUri(provider))
                + "&scope=" + enc("openid profile email")
                + "&state=" + enc(txn.state())
                + "&nonce=" + enc(txn.nonce())
                + "&code_challenge_method=S256&code_challenge=" + enc(txn.challenge());
        return new StartResponse(url, txn.state());
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable String provider, @RequestParam(required=false) String code,
                         @RequestParam(required=false) String state,
                         HttpServletResponse response) throws java.io.IOException {
        // Never reflect provider error descriptions, tokens or credentials in the redirect.
        try {
            SocialLoginStateService.Transaction tx = states.consume(provider, state);
            SocialIdentityService.Identity identity = verifyIdentity(provider, code, tx);
            String ticket = identities.resolveAndTicket(tx, identity);
            response.sendRedirect(frontend.replaceAll("/+$", "") + "/social/callback?state=" + enc(state) + "&ticket=" + enc(ticket));
        } catch (ResponseStatusException ex) {
            String reason = switch (ex.getStatusCode().value()) {
                case 409 -> "account_link_required";
                case 403 -> "unverified_email";
                default -> "oauth_failed";
            };
            response.sendRedirect(frontend.replaceAll("/+$", "") + "/social/callback?error=" + reason);
        } catch (Exception ex) {
            response.sendRedirect(frontend.replaceAll("/+$", "") + "/social/callback?error=oauth_failed");
        }
    }

    @PostMapping("/finish")
    public ResponseEntity<FinishResponse> finish(@RequestBody FinishRequest request,
                                                   HttpServletRequest httpRequest,
                                                   HttpServletResponse response) {
        SocialIdentityService.FinishResult finished = identities.finish(request.ticket(), request.otp(), httpRequest.getRemoteAddr());
        if (finished.mfaRequired()) return ResponseEntity.ok(new FinishResponse(true, null));
        ResponseCookie cookie = ResponseCookie.from("rk_refresh_token", finished.result().refreshToken())
                .httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite)
                .path("/api/v1/auth").maxAge(Duration.ofDays(refreshDays)).build();
        response.addHeader("Set-Cookie", cookie.toString());
        return ResponseEntity.ok(new FinishResponse(false, finished.result().response()));
    }

    private SocialIdentityService.Identity verifyIdentity(String provider, String code, SocialLoginStateService.Transaction txn) {
        Config c = config(provider);
        if (code == null || code.isBlank() || code.length() > 4096)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authorization code");
        try {
            String body = "grant_type=authorization_code&code=" + enc(code)
                    + "&redirect_uri=" + enc(redirectUri(provider))
                    + "&client_id=" + enc(c.clientId()) + "&client_secret=" + enc(c.clientSecret())
                    + "&code_verifier=" + enc(txn.verifier());
            String raw = http.post().uri(c.token()).header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body).retrieve().body(String.class);
            JsonNode token = mapper.readTree(raw);
            String signed = token.path("id_token").asText("");
            if (signed.isBlank()) throw new IllegalArgumentException("Missing ID token");
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(c.jwks()).build();
            decoder.setJwtValidator(JwtValidators.createDefault());
            Jwt jwt = decoder.decode(signed);
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !jwt.getAudience().contains(c.clientId())
                    || !txn.nonce().equals(jwt.getClaimAsString("nonce"))
                    || (jwt.getAudience().size() > 1 && !c.clientId().equals(jwt.getClaimAsString("azp"))))
                throw new IllegalArgumentException("OIDC claim mismatch");
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            if (provider.equals("google")) {
                if (!issuer.equals("https://accounts.google.com")) throw new IllegalArgumentException("Invalid Google issuer");
            } else {
                String tenantId = jwt.getClaimAsString("tid");
                if (tenantId == null || !tenantId.matches("[0-9a-fA-F-]{36}")
                        || !issuer.equals("https://login.microsoftonline.com/" + tenantId + "/v2.0"))
                    throw new IllegalArgumentException("Invalid Microsoft issuer");
            }
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            boolean verified = Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                    || "true".equalsIgnoreCase(String.valueOf(jwt.getClaim("email_verified")));
            return new SocialIdentityService.Identity(issuer, jwt.getSubject(), email, name, verified);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social identity verification failed");
        }
    }

    private Config config(String provider) {
        Config c = switch (provider) {
            case "google" -> new Config(googleId, googleSecret,
                    "https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
                    "https://www.googleapis.com/oauth2/v3/certs", "https://accounts.google.com");
            case "microsoft" -> new Config(microsoftId, microsoftSecret,
                    "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    "https://login.microsoftonline.com/common/discovery/v2.0/keys", "");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported social provider");
        };
        if (c.clientId() == null || c.clientId().isBlank() || c.clientSecret() == null || c.clientSecret().isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Social provider not configured");
        return c;
    }
    private String redirectUri(String provider) {
        return callbackBase.replaceAll("/+$", "") + "/api/v1/auth/social/" + provider + "/callback";
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
