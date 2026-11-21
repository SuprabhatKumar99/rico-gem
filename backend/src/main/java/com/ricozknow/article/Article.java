package com.ricozknow.article;

import com.ricozknow.category.Category;
import com.ricozknow.common.TenantOwnedEntity;
import com.ricozknow.search.IndexStatus;
import com.ricozknow.enterprise.group.Group;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor
public class Article extends TenantOwnedEntity {

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 16)
    private String language = "en";

    @Enumerated(EnumType.STRING)
    @Column(name = "article_type", nullable = false)
    private ArticleType articleType = ArticleType.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PRIVATE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_version_id")
    private ArticleVersion activeVersion;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="visibility_group_id")
    private Group visibilityGroup;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_index_status", nullable = false)
    private IndexStatus searchIndexStatus = IndexStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "rag_index_status", nullable = false)
    private IndexStatus ragIndexStatus = IndexStatus.PENDING;

    @Column(name = "search_indexed_at")
    private java.time.Instant searchIndexedAt;

    @Column(name = "rag_indexed_at")
    private java.time.Instant ragIndexedAt;

    @Column(name = "search_index_error", columnDefinition = "text")
    private String searchIndexError;

    @Column(name = "rag_index_error", columnDefinition = "text")
    private String ragIndexError;

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(nullable = false)
    private boolean featured = false;

    @Column(name="owner_id") private UUID ownerId;
    @Column(name="review_interval_days", nullable=false) private int reviewIntervalDays = 180;
    @Column(name="next_review_at") private java.time.Instant nextReviewAt;
    @Column(name="verified_at") private java.time.Instant verifiedAt;
    @Column(name="verified_by") private UUID verifiedBy;
    @Column(name="review_due_notified_at") private java.time.Instant reviewDueNotifiedAt;

    @Column(name = "scheduled_publish_at")
    private java.time.Instant scheduledPublishAt;

    @Column(name = "scheduled_unpublish_at")
    private java.time.Instant scheduledUnpublishAt;
}
