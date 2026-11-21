package com.ricozknow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialLoginStateServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String,String> values;

    @Test void stateBindsProviderTenantAndLinkUserAndIsConsumedOnce() {
        AtomicReference<String> stored = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5))))
                .thenAnswer(invocation -> { stored.set(invocation.getArgument(1)); return true; });
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> stored.getAndSet(null));
        SocialLoginStateService service = new SocialLoginStateService(redis, new ObjectMapper());
        UUID account = UUID.randomUUID();
        var started = service.start("acme", "google", account);
        assertThat(started.state()).hasSize(43);
        assertThat(started.nonce()).hasSize(43).isNotEqualTo(started.state());
        assertThat(started.challenge()).hasSize(43);
        assertThatThrownBy(() -> service.consume("microsoft", started.state()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.consume("google", started.state()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void validStateCannotBeReplayed() {
        AtomicReference<String> stored = new AtomicReference<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), eq(Duration.ofMinutes(5))))
                .thenAnswer(invocation -> { stored.set(invocation.getArgument(1)); return true; });
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> stored.getAndSet(null));
        SocialLoginStateService service = new SocialLoginStateService(redis, new ObjectMapper());
        var started = service.start("acme", "google", UUID.randomUUID());
        var tx = service.consume("google", started.state());
        assertThat(tx.tenantSlug()).isEqualTo("acme");
        assertThat(tx.verifier()).hasSize(43);
        assertThatThrownBy(() -> service.consume("google", started.state()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
