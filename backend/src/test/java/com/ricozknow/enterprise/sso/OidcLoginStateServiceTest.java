package com.ricozknow.enterprise.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OidcLoginStateServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    @Test void stateIsRandomPkceBoundAndSingleUse() {
        AtomicReference<String> stored = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5))))
                .thenAnswer(invocation -> { stored.set(invocation.getArgument(1)); return true; });
        when(redis.opsForValue().getAndDelete(anyString())).thenAnswer(invocation -> stored.getAndSet(null));
        OidcLoginStateService service = new OidcLoginStateService(redis, new ObjectMapper());
        var started = service.create("tenant-a", "https://wiki.example.com/sso/callback");
        assertThat(started.state()).hasSize(43);
        assertThat(started.nonce()).isNotEqualTo(started.state());
        assertThat(started.codeChallenge()).hasSize(43).isNotEqualTo(started.codeVerifier());
        var redeemed = service.consume(started.state());
        assertThat(redeemed.tenantSlug()).isEqualTo("tenant-a");
        assertThat(redeemed.nonce()).isEqualTo(started.nonce());
        assertThat(redeemed.codeVerifier()).isEqualTo(started.codeVerifier());
        assertThatThrownBy(() -> service.consume(started.state())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void invalidStateIsRejectedWithoutRedisLookup() {
        OidcLoginStateService service = new OidcLoginStateService(redis, new ObjectMapper());
        assertThatThrownBy(() -> service.consume("dGVuYW50LWE=")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(redis);
    }
}
