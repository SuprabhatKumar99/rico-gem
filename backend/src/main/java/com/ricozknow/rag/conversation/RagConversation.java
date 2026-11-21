package com.ricozknow.rag.conversation;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="rag_conversations", indexes={@Index(name="idx_rag_conv_tenant_updated", columnList="tenant_id,updated_at")})
@Getter @Setter @NoArgsConstructor
public class RagConversation {
 @Id private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(nullable=false,length=200) private String title;
 @Column(name="owner_key_id") private UUID ownerKeyId;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
}
