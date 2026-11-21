package com.ricozknow.auth;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the security-critical lock cannot disappear in a repository refactor. */
class RefreshTokenLockContractTest {
    @Test void hashLookupUsesExclusiveJpaRowLock() throws Exception {
        var method = RefreshTokenRepository.class.getDeclaredMethod("findByTokenHash", String.class);
        var lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, "Refresh-token lookup must serialize concurrent rotations");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
