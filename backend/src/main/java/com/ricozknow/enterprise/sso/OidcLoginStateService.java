package com.ricozknow.enterprise.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/** One-time, server-side OIDC transaction state prevents login CSRF and replay. */
@Service
@RequiredArgsConstructor
public class OidcLoginStateService {
    private static final String PREFIX = "rk:oidc:state:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public record Transaction(String tenantSlug, String redirectUri, String nonce, String codeVerifier) {}
    public record Started(String state, String nonce, String codeVerifier, String codeChallenge) {}

    public Started create(String tenantSlug, String redirectUri) {
        String state = randomValue();
        String nonce = randomValue();
        String verifier = randomValue();
        try {
            String stored = mapper.writeValueAsString(new Transaction(tenantSlug, redirectUri, nonce, verifier));
            Boolean success = redis.opsForValue().setIfAbsent(PREFIX + state, stored, Duration.ofMinutes(5));
            if (!Boolean.TRUE.equals(success)) throw new IllegalStateException("Could not reserve OIDC state");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return new Started(state, nonce, verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (Exception e) {
            throw new IllegalStateException("Could not create OIDC state", e);
        }
    }

    public Transaction consume(String state) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) invalid();
        String value = redis.opsForValue().getAndDelete(PREFIX + state);
        if (value == null) invalid();
        try {
            return mapper.readValue(value, Transaction.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OIDC state", e);
        }
    }

    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void invalid() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OIDC state expired, invalid or already used");
    }
}
