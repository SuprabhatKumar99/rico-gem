package com.ricozknow.rag.feedback;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.conversation.RagConversationRepository;
import com.ricozknow.rag.conversation.RagMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import com.ricozknow.rag.RagApiException;
import com.ricozknow.rag.RagApiAccess;

@Service @RequiredArgsConstructor
public class RagFeedbackService {
    private final RagFeedbackRepository repository;
    private final RagConversationRepository conversations;
    private final RagMessageRepository messages;
    private final AuditService auditService;

    @Transactional
    public RagFeedbackResponse upsert(RagFeedbackRequest request) {
        UUID tenant = TenantContext.get();
        if (conversations.findByTenantIdAndIdAndOwnerKeyId(tenant, request.conversationId(), RagApiAccess.keyId()).isEmpty()) throw new RagApiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND, false);
        if (request.messageId() != null && messages.findByTenantIdAndId(tenant, request.messageId())
                .filter(message -> message.getConversationId().equals(request.conversationId())).isEmpty())
            throw new RagApiException("MESSAGE_NOT_FOUND", "Message not found", HttpStatus.NOT_FOUND, false);
        RagFeedback f = repository.findByTenantIdAndConversationIdAndMessageId(tenant, request.conversationId(), request.messageId()).orElseGet(() -> { RagFeedback x = new RagFeedback(); x.setId(UUID.randomUUID()); x.setTenantId(tenant); x.setConversationId(request.conversationId()); x.setMessageId(request.messageId()); x.setCreatedAt(Instant.now()); return x; });
        f.setHelpful(request.helpful()); f.setComment(request.comment()); f.setCreatedAt(Instant.now());
        f = repository.save(f);
        auditService.record("RAG_FEEDBACK", f.getId(), "RAG_FEEDBACK_SUBMITTED", null, f);
        return new RagFeedbackResponse(f.getId(), f.getConversationId(), f.getMessageId(), f.isHelpful(), f.getComment(), f.getCreatedAt());
    }
}
