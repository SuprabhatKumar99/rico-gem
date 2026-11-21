package com.ricozknow.customer;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CustomerMagicLinkLockContractTest {
    @Test void redemptionUsesTenantScopedExclusiveRowLock() throws Exception {
        var method = CustomerMagicLinkRepository.class.getDeclaredMethod("lockByTenantIdAndTokenHash", UUID.class, String.class);
        var lock = method.getAnnotation(Lock.class);
        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
        assertTrue(method.getAnnotation(Query.class).value().contains("l.tenantId = :tenantId"));
    }
}
