package com.ricozknow.customer;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customer/auth")
@RequiredArgsConstructor
public class CustomerAuthController {
    private static final String COOKIE = "rk_customer_session";
    private final CustomerAuthService service;

    @Value("${ricozknow.customer.cookie-secure:${RICOZ_CUSTOMER_COOKIE_SECURE:false}}")
    boolean secure;
    @Value("${ricozknow.customer.cookie-same-site:${RICOZ_CUSTOMER_COOKIE_SAMESITE:Lax}}")
    String sameSite;

    @PostMapping("/magic-link")
    public ResponseEntity<Void> request(@Valid @RequestBody Request request) {
        service.request(request.tenantSlug(), request.email());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@CookieValue(name = COOKIE, required = false) String token) {
        CustomerUser user = service.current(token);
        return user == null ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(Map.of("id", user.getId(), "email", user.getEmail(),
                        "name", user.getName() == null ? "" : user.getName()));
    }

    /** The one-time credential belongs in the JSON body, not in URL/access logs. */
    @PostMapping("/consume")
    public ResponseEntity<Void> consume(@Valid @RequestBody ConsumeRequest request, HttpServletResponse response) {
        CustomerAuthService.LoginSession login = service.consume(request.token());
        response.addHeader("Set-Cookie", cookie(login.rawToken(), Duration.ofDays(30)).toString());
        return ResponseEntity.noContent().build();
    }

    /** Revokes only the current tenant's session; clearing the cookie is not sufficient. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token,
                                       HttpServletResponse response) {
        service.revoke(token);
        response.addHeader("Set-Cookie", cookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure)
                .sameSite(sameSite).path("/").maxAge(age).build();
    }

    public record Request(@NotBlank String tenantSlug, @Email @NotBlank String email) {}
    public record ConsumeRequest(@NotBlank String token) {}
}
