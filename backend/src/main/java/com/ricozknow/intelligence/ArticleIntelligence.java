package com.ricozknow.intelligence;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "article_intelligence")
@Getter @Setter @NoArgsConstructor
public class ArticleIntelligence extends TenantOwnedEntity {
    @Column(name = "article_id", nullable = false, unique = true)
    private UUID articleId;
    @Column(name = "version_id") private UUID versionId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String keywords = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String topics = "[]";
    @Column(name = "health_score", nullable = false) private double healthScore;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "health_reasons", nullable = false, columnDefinition = "jsonb") private String healthReasons = "[]";
    @Column(name = "analyzed_at") private Instant analyzedAt;
}
