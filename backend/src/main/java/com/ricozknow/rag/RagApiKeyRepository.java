package com.ricozknow.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RagApiKeyRepository extends JpaRepository<RagApiKey, UUID> {
    Optional<RagApiKey> findByKeyHashAndStatus(String keyHash, RagApiKey.Status status);
    List<RagApiKey> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
