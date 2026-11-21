package com.ricozknow.intelligence;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="knowledge_gap_tickets") @Getter @Setter @NoArgsConstructor
public class KnowledgeGapTicket extends TenantOwnedEntity {
    @Column(name="query_text", nullable=false, length=500) private String queryText;
    @Column(nullable=false) private int volume;
    @Column(name="zero_result_count", nullable=false) private int zeroResultCount;
    @Column(name="unsuccessful_count", nullable=false) private int unsuccessfulCount;
    @Column(name="gap_score", nullable=false) private int gapScore;
    @Column(nullable=false, length=32) private String status = "OPEN";
    @Column(name="suggested_title", length=300) private String suggestedTitle;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="suggested_outline", nullable=false, columnDefinition="jsonb") private String suggestedOutline = "[]";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="suggested_keywords", nullable=false, columnDefinition="jsonb") private String suggestedKeywords = "[]";
    @Column(name="assigned_to") private UUID assignedTo;
    @Column(name="article_id") private UUID articleId;
}
