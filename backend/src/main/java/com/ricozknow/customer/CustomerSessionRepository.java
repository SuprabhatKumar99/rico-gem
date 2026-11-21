package com.ricozknow.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerSessionRepository extends JpaRepository<CustomerSession, UUID> {
    Optional<CustomerSession> findByTokenHash(String tokenHash);
    Optional<CustomerSession> findByTenantIdAndTokenHash(UUID tenantId, String tokenHash);
}
