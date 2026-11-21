package com.ricozknow.auth;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LoginRateLimiterTest {
    @Test void bothAccountAndIpAreIncrementedWithoutLeakingEmailIntoKeys() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        when(values.get(anyString())).thenReturn(null);
        var limiter = new LoginRateLimiter(redis);
        ReflectionTestUtils.setField(limiter, "maxAttempts", 5);
        ReflectionTestUtils.setField(limiter, "ipMaxAttempts", 30);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 15L);
        limiter.recordFailure("tenant-a", "user@example.com", "1.2.3.4");
        var keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).increment(keys.capture());
        assertThat(keys.getAllValues()).allMatch(k -> !k.contains("user@example.com") && !k.contains("1.2.3.4"));
        assertThat(keys.getAllValues().get(0)).startsWith("login-account:");
        assertThat(keys.getAllValues().get(1)).startsWith("login-ip:");
        verify(redis, times(2)).expire(anyString(), eq(Duration.ofMinutes(15)));
        limiter.recordSuccess("tenant-a", "user@example.com", "1.2.3.4");
        verify(redis).delete(keys.getAllValues().get(0));
        verify(redis, never()).delete(keys.getAllValues().get(1));
    }

    @Test void networkOrAccountLimitBlocksAuthentication() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("30");
        var limiter = new LoginRateLimiter(redis);
        ReflectionTestUtils.setField(limiter, "maxAttempts", 5);
        ReflectionTestUtils.setField(limiter, "ipMaxAttempts", 30);
        assertThat(limiter.isBlocked("tenant-a", "user@example.com", "1.2.3.4")).isTrue();
    }
}
