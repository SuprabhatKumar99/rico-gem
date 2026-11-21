package com.ricozknow.audit;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditLogImmutabilityTest {
    @Test void jpaLifecycleAlwaysRejectsMutation() {
        AuditLog log = new AuditLog();
        assertThatThrownBy(log::rejectMutation)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("append-only");
    }
}
