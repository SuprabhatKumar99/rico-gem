package com.ricozknow.search;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="saved_searches")
@Getter @Setter @NoArgsConstructor
public class SavedSearch {
 @Id @GeneratedValue private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(nullable=false,length=320) private String email;
 @Column(name="query_text",nullable=false,length=500) private String queryText;
 @Column(name="category_id") private UUID categoryId;
 private String language;
 private String tag;
 @Column(name="article_type") private String articleType;
 @Column(name="date_from") private Instant dateFrom;
 @Column(name="date_to") private Instant dateTo;
 @Column(nullable=false) private boolean active=true;
 @Column(name="last_checked_at") private Instant lastCheckedAt;
 @Column(name="last_notified_article_id") private UUID lastNotifiedArticleId;
 @Column(name="last_notified_source_id") private String lastNotifiedSourceId;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
}
