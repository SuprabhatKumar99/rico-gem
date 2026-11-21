package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
@Entity @Table(name="article_content_templates") @Getter @Setter @NoArgsConstructor
public class ArticleContentTemplate extends TenantOwnedEntity {
 @Column(name="template_key",nullable=false,length=120) private String templateKey;
 @Column(nullable=false,length=180) private String name;
 @Column(columnDefinition="text") private String description;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private JsonNode content;
 @Column(name="created_by",nullable=false) private UUID createdBy;
}
