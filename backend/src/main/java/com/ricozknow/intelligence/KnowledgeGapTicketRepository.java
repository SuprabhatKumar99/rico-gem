package com.ricozknow.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;


public interface KnowledgeGapTicketRepository extends JpaRepository<KnowledgeGapTicket, UUID> {
    Optional<KnowledgeGapTicket> findByTenantIdAndQueryText(UUID tenantId, String queryText);
    List<KnowledgeGapTicket> findTop100ByTenantIdAndStatusOrderByGapScoreDesc(UUID tenantId, String status);

    Optional<KnowledgeGapTicket> findByTenantIdAndId(UUID tenantId, UUID id);
}
