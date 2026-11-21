package com.ricozknow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
    Page<User> findByTenantId(UUID tenantId, Pageable pageable);
}
