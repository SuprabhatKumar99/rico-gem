package com.ricozknow.rag.feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface RagFeedbackRepository extends JpaRepository<RagFeedback, UUID> {
    Optional<RagFeedback> findByTenantIdAndConversationIdAndMessageId(UUID tenantId, UUID conversationId, UUID messageId);
}
