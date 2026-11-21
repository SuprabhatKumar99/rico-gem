package com.ricozknow.auth;

import com.ricozknow.auth.dto.LoginRequest;
import com.ricozknow.auth.dto.LoginResponse;
import com.ricozknow.auth.dto.LoginResult;
import com.ricozknow.auth.dto.RegisterRequest;
import com.ricozknow.auth.dto.RegisterResponse;
import com.ricozknow.auth.dto.ForgotPasswordRequest;
import com.ricozknow.auth.dto.ResetPasswordRequest;
import com.ricozknow.auth.dto.VerifyEmailRequest;
import com.ricozknow.auth.dto.ResendVerificationRequest;
import com.ricozknow.auth.dto.AcceptInvitationRequest;
import com.ricozknow.user.UserService;
import com.ricozknow.user.dto.AcceptInvitationResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Long-lived refresh credentials are stored in HttpOnly cookies. Cookie-based
 * refresh/logout requests are additionally checked by CookieOriginGuardFilter,
 * independent of the selected SameSite deployment setting.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "rk_refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;

    @Value("${ricozknow.security.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @Value("${ricozknow.security.jwt.refresh-cookie-secure}")
    private boolean refreshCookieSecure;

    @Value("${ricozknow.security.jwt.refresh-cookie-same-site:Lax}")
    private String refreshCookieSameSite;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        LoginResult result = authService.login(request, clientIp(httpRequest), httpRequest.getHeader("X-Device-Id"), httpRequest.getHeader("X-Device-Name"), httpRequest.getHeader("User-Agent"));
        setRefreshCookie(httpResponse, result.refreshToken());
        return ResponseEntity.ok(result.response());
    }

    /**
     * Silent refresh: the frontend calls this when an access token is about
     * to expire (or has just expired), using the httpOnly cookie the browser
     * sends automatically — no refresh token ever touches frontend JavaScript.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String presented = readRefreshCookie(httpRequest);
        if (presented == null) {
            throw new AuthException("No refresh token presented");
        }
        LoginResult result = authService.refresh(presented);
        setRefreshCookie(httpResponse, result.refreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(readRefreshCookie(httpRequest));
        clearRefreshCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    // private void setRefreshCookie(HttpServletResponse response, String token) {
    //     ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
    //             .httpOnly(true)
    //             .secure(refreshCookieSecure) // requires HTTPS; see ricozknow.security.jwt.refresh-cookie-secure
    //             .sameSite("Strict")
    //             .path(REFRESH_COOKIE_PATH)
    //             .maxAge(Duration.ofDays(refreshTokenTtlDays))
    //             .build();
    //     response.addHeader("Set-Cookie", cookie.toString());
    // }

    private void setRefreshCookie(
            HttpServletResponse response,
            String token
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(refreshTokenTtlDays))
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    // private void clearRefreshCookie(HttpServletResponse response) {
    //     ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
    //             .httpOnly(true).secure(refreshCookieSecure).sameSite("Strict").path(REFRESH_COOKIE_PATH).maxAge(0).build();
    //     response.addHeader("Set-Cookie", cookie.toString());
    // }

    private void clearRefreshCookie(
            HttpServletResponse response
    ) {
        ResponseCookie cookie = ResponseCookie
                .from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite(refreshCookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();

        response.addHeader(
                "Set-Cookie",
                cookie.toString()
        );
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For is client-controlled unless an explicitly trusted proxy
        // sanitizes it. Never trust the raw header for security rate limits.
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.tenantSlug(), request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Deliberately identical response for known/unknown accounts to avoid account enumeration.
        authService.requestPasswordReset(request.tenantSlug(), request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        UserService.AcceptInvitationResult result = userService.acceptInvitation(request.token(), request.name(), request.password());
        return ResponseEntity.ok(new AcceptInvitationResponse(result.userId(), result.tenantId(), result.email(), "Invitation accepted"));
    }
}