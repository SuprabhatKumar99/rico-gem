package com.ricozknow.enterprise.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/** Shared Redis throttle applies equally to login OTP, recovery codes and MFA settings. */
@Component
@RequiredArgsConstructor
public class MfaAttemptLimiter {
    private final StringRedisTemplate redis;
    @Value("${ricozknow.security.mfa.max-attempts:5}") int maxAttempts;
    @Value("${ricozknow.security.mfa.window-minutes:15}") long windowMinutes;

    public boolean isBlocked(UUID tenant, UUID user) {
        String count = redis.opsForValue().get(key(tenant, user));
        return count != null && Long.parseLong(count) >= maxAttempts;
    }

    public void recordFailure(UUID tenant, UUID user) {
        String key = key(tenant, user);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(windowMinutes));
    }

    public void recordSuccess(UUID tenant, UUID user) { redis.delete(key(tenant, user)); }
    private static String key(UUID tenant, UUID user) { return "mfa-attempt:" + tenant + ":" + user; }
}
