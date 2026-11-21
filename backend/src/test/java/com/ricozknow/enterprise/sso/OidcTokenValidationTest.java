package com.ricozknow.enterprise.sso;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OidcTokenValidationTest {
    private Jwt jwt(String nonce, List<String> audience, String azp) {
        return Jwt.withTokenValue("signed-test-jwt")
                .header("alg", "RS256")
                .subject("provider-subject")
                .audience(audience)
                .claim("nonce", nonce)
                .claim("azp", azp)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test void mismatchedNonceOrAudienceRejected() {
        assertThatThrownBy(() -> SsoService.validateIdToken(jwt("nonce-a", List.of("client-a"), "client-a"), "client-a", "nonce-b"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> SsoService.validateIdToken(jwt("nonce-a", List.of("client-b"), "client-b"), "client-a", "nonce-a"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> SsoService.validateIdToken(jwt("nonce-a", List.of("client-a", "other"), "other"), "client-a", "nonce-a"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> SsoService.validateIdToken(jwt("nonce-a", List.of("client-a"), "client-a"), "client-a", "nonce-a"))
                .doesNotThrowAnyException();
    }
}
