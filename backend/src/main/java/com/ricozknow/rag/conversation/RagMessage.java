package com.ricozknow.rag.conversation;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="rag_messages", indexes={@Index(name="idx_rag_msg_conv_created", columnList="conversation_id,created_at")})
@Getter @Setter @NoArgsConstructor
public class RagMessage {
 @Id private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(name="conversation_id",nullable=false) private UUID conversationId;
 @Column(nullable=false,length=20) private String role;
 @Column(nullable=false,columnDefinition="text") private String content;
 @Column(name="created_at",nullable=false) private Instant createdAt;
}
