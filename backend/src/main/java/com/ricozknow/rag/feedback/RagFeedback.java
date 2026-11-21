package com.ricozknow.rag.feedback;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="rag_feedback", indexes={@Index(name="idx_rag_feedback_tenant_created", columnList="tenant_id,created_at"), @Index(name="idx_rag_feedback_conversation", columnList="conversation_id")})
@Getter @Setter @NoArgsConstructor
public class RagFeedback {
    @Id private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="conversation_id", nullable=false) private UUID conversationId;
    @Column(name="message_id") private UUID messageId;
    @Column(nullable=false) private boolean helpful;
    @Column(length=1000) private String comment;
    @Column(name="created_at", nullable=false) private Instant createdAt;
}
