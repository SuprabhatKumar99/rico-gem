package com.ricozknow.customer;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="customer_magic_links")
@Getter @Setter @NoArgsConstructor
public class CustomerMagicLink {
 @Id @GeneratedValue private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="customer_id",nullable=false) private CustomerUser customer;
 @Column(name="token_hash",nullable=false,unique=true) private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt;
 private Instant usedAt;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 public boolean active(){return usedAt==null && expiresAt.isAfter(Instant.now());}
}