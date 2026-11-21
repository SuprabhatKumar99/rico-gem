package com.ricozknow.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Redis-backed account AND network throttles. Both counters are incremented on each
 * failure, so cycling IPs or usernames cannot evade both bounds. Keys hash PII.
 * Rate limiting intentionally fails closed when Redis is unavailable.
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {
    private final StringRedisTemplate redis;
    @Value("${ricozknow.security.login-rate-limit.max-attempts:5}") private int maxAttempts;
    @Value("${ricozknow.security.login-rate-limit.ip-max-attempts:30}") private int ipMaxAttempts;
    @Value("${ricozknow.security.login-rate-limit.window-minutes:15}") private long windowMinutes;

    public boolean isBlocked(String tenantSlug, String email, String clientIp) {
        return count(accountKey(tenantSlug, email)) >= maxAttempts || count(ipKey(tenantSlug, clientIp)) >= ipMaxAttempts;
    }

    public void recordFailure(String tenantSlug, String email, String clientIp) {
        increment(accountKey(tenantSlug, email));
        increment(ipKey(tenantSlug, clientIp));
    }

    public void recordSuccess(String tenantSlug, String email, String clientIp) {
        // A legitimate user's successful login clears account failures only;
        // do not clear IP-wide failures or one known account could reset the IP limit.
        redis.delete(accountKey(tenantSlug, email));
    }

    private long count(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, Duration.ofMinutes(windowMinutes));
    }

    private static String accountKey(String tenant, String email) {
        return "login-account:" + digest(tenant.trim().toLowerCase(Locale.ROOT) + ":" + email.trim().toLowerCase(Locale.ROOT));
    }
    private static String ipKey(String tenant, String ip) {
        return "login-ip:" + digest(tenant.trim().toLowerCase(Locale.ROOT) + ":" + ip);
    }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
