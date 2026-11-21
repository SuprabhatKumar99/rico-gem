package com.ricozknow.enterprise.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.auth.dto.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class SsoLoginHandoffService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public String create(LoginResult result) {
        try {
            String code = randomCode();
            redis.opsForValue().set("rk:sso:handoff:" + code,
                    mapper.writeValueAsString(result), Duration.ofSeconds(60));
            return code;
        } catch (Exception e) {
            throw new IllegalStateException("Could not create SSO handoff", e);
        }
    }

    public LoginResult consume(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Missing SSO handoff code");
        try {
            String key = "rk:sso:handoff:" + code;
            String value = redis.opsForValue().getAndDelete(key);
            if (value == null) throw new IllegalArgumentException("SSO handoff expired or already used");
            return mapper.readValue(value, LoginResult.class);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not consume SSO handoff", e);
        }
    }

    private String randomCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
