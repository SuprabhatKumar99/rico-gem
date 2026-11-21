package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity @Table(name="article_review_decisions") @Getter @Setter @NoArgsConstructor
public class ArticleReviewDecision extends TenantOwnedEntity {
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="article_id",nullable=false) private Article article;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="version_id",nullable=false) private ArticleVersion version;
 @Column(name="reviewer_id",nullable=false) private UUID reviewerId;
 @Column(nullable=false) private String decision;
 @Column(columnDefinition="text") private String comment;
}
