package com.ricozknow.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.user.User;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditStateSanitizerTest {
    private final AuditStateSanitizer sanitizer = new AuditStateSanitizer(new ObjectMapper());

    @Test void userAuditNeverSerializesPasswordOrMfaSecret() {
        User user = new User();
        user.setEmail("a@example.com");
        user.setPasswordHash("hashed-secret");
        user.setMfaSecret("totp-secret");
        String text = sanitizer.safeJson(user);
        assertThat(text).contains("a@example.com").doesNotContain("hashed-secret", "totp-secret");
    }

    @Test void deeplyNestedSecretsAreRedacted() {
        String text = sanitizer.safeJson(Map.of("metadata", Map.of("api_key", "secret-value", "name", "hello")));
        assertThat(text).contains("[REDACTED]", "hello").doesNotContain("secret-value");
    }
}
