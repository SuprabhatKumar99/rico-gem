package com.ricozknow.rag.conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RagMessageRepository extends JpaRepository<RagMessage,UUID> { List<RagMessage> findTop20ByTenantIdAndConversationIdOrderByCreatedAtDesc(UUID tenantId, UUID conversationId); Optional<RagMessage> findByTenantIdAndId(UUID tenantId, UUID id); }
