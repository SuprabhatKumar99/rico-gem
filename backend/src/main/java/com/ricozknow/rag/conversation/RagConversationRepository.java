package com.ricozknow.rag.conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RagConversationRepository extends JpaRepository<RagConversation,UUID> {
 Optional<RagConversation> findByTenantIdAndIdAndOwnerKeyId(UUID tenantId, UUID id, UUID ownerKeyId);
 List<RagConversation> findAllByTenantIdAndOwnerKeyIdOrderByUpdatedAtDesc(UUID tenantId, UUID ownerKeyId);
}
