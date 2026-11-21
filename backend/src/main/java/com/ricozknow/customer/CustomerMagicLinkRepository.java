package com.ricozknow.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface CustomerMagicLinkRepository extends JpaRepository<CustomerMagicLink, UUID> {
    Optional<CustomerMagicLink> findByTokenHash(String tokenHash);

    /** Called within CustomerAuthService.consume's write transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CustomerMagicLink l where l.tenantId = :tenantId and l.tokenHash = :tokenHash")
    Optional<CustomerMagicLink> lockByTenantIdAndTokenHash(@Param("tenantId") UUID tenantId,
                                                           @Param("tokenHash") String tokenHash);
}
