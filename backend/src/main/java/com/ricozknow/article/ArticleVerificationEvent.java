package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity @Table(name="article_verification_events") @Getter @Setter @NoArgsConstructor
public class ArticleVerificationEvent extends TenantOwnedEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="article_id",nullable=false) private Article article;
 @Column(name="actor_id") private UUID actorId;
 @Column(nullable=false) private String action;
 @Column(columnDefinition="text") private String details;
}
