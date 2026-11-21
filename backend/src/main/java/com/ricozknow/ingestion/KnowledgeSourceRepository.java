package com.ricozknow.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

public interface KnowledgeSourceRepository extends JpaRepository<KnowledgeSource, UUID> {
    Optional<KnowledgeSource> findByTenantIdAndId(UUID tenantId, UUID id);
    List<KnowledgeSource> findByStatusAndNextSyncAtBefore(KnowledgeSourceStatus status, Instant now);
    List<KnowledgeSource> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
