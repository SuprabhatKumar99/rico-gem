package com.ricozknow.rag.conversation;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.dto.RagConversationResponse;
import com.ricozknow.rag.RagApiAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import com.ricozknow.rag.RagApiException;

@Service @RequiredArgsConstructor
public class RagConversationService {
    private final RagConversationRepository conversations;
    private final RagMessageRepository messages;
    private final AuditService auditService;

    @Transactional(readOnly=true)
    public List<RagConversationResponse> list() {
        UUID tenant=TenantContext.get();
        return conversations.findAllByTenantIdAndOwnerKeyIdOrderByUpdatedAtDesc(tenant,RagApiAccess.keyId()).stream().map(c -> toResponse(c, 20)).toList();
    }
    @Transactional
    public RagConversationResponse create(String title) {
        UUID tenant=TenantContext.get(); RagConversation c=new RagConversation(); c.setId(UUID.randomUUID()); c.setTenantId(tenant); c.setOwnerKeyId(RagApiAccess.keyId()); c.setTitle(normalizeTitle(title)); c.setCreatedAt(Instant.now()); c.setUpdatedAt(Instant.now()); c=conversations.save(c); auditService.record("RAG_CONVERSATION", c.getId(), "RAG_CONVERSATION_CREATED", null, c); return toResponse(c,0);
    }
    @Transactional
    public RagConversationResponse rename(UUID id, String title) {
        RagConversation c = findConversation(id);
        c.setTitle(normalizeTitle(title)); 
        c.setUpdatedAt(Instant.now()); 
        c=conversations.save(c); 

        auditService.record("RAG_CONVERSATION", c.getId(), "RAG_CONVERSATION_RENAMED", null, c); 
        
        return toResponse(c,20);
    }
    @Transactional
    public void delete(UUID id) { RagConversation c=findConversation(id); conversations.delete(c); auditService.record("RAG_CONVERSATION", id, "RAG_CONVERSATION_DELETED", c, null); }
    @Transactional(readOnly=true)
    public RagConversationResponse get(UUID id) { return toResponse(findConversation(id),20); }
    private RagConversation findConversation(UUID id){ return conversations.findByTenantIdAndIdAndOwnerKeyId(TenantContext.get(),id,RagApiAccess.keyId()).orElseThrow(()->new RagApiException("CONVERSATION_NOT_FOUND", "Conversation not found", HttpStatus.NOT_FOUND, false)); }
    private RagConversationResponse toResponse(RagConversation c,int limit){
        List<RagMessage> ms=limit==0?List.of():messages.findTop20ByTenantIdAndConversationIdOrderByCreatedAtDesc(TenantContext.get(),c.getId()).stream().sorted(Comparator.comparing(RagMessage::getCreatedAt)).toList();
        return new RagConversationResponse(c.getId(),c.getTitle(),c.getCreatedAt(),c.getUpdatedAt(),ms.stream().map(m->new RagConversationResponse.Message(m.getId(),m.getRole(),m.getContent(),m.getCreatedAt())).toList());
    }
    private String normalizeTitle(String t){ String v=(t==null||t.isBlank())?"New conversation":t.trim(); return v.length()>200?v.substring(0,200):v; }
}
