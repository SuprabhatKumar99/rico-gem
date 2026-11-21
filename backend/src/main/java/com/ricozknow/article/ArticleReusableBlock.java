package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity @Table(name="article_reusable_blocks") @Getter @Setter @NoArgsConstructor
public class ArticleReusableBlock extends TenantOwnedEntity {
 @Column(nullable=false,length=180) private String name;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private JsonNode block;
 @Column(name="created_by",nullable=false) private UUID createdBy;
}
