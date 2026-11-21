package com.ricozknow.intelligence;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="article_feedback_classifications") @Getter @Setter @NoArgsConstructor
public class ArticleFeedbackClassification {
    @Id @GeneratedValue private UUID id;
    @Column(name="tenant_id", nullable=false) private UUID tenantId;
    @Column(name="analytics_event_id", nullable=false, unique=true) private UUID analyticsEventId;
    @Column(name="article_id") private UUID articleId;
    @Column(nullable=false, length=64) private String reason;
    @Column(nullable=false) private double confidence;
    @Column(length=500) private String rationale;
    @Column(name="created_at", nullable=false) private Instant createdAt = Instant.now();
}
