package com.ricozknow.article;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="article_block_comments")
@Getter @Setter @NoArgsConstructor
public class ArticleBlockComment extends TenantOwnedEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="article_id",nullable=false) private Article article;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="version_id",nullable=false) private ArticleVersion version;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="parent_id") private ArticleBlockComment parent;
    @Column(name="block_id",nullable=false,length=100) private String blockId;
    @Column(name="author_id",nullable=false) private UUID authorId;
    @Column(nullable=false,columnDefinition="text") private String comment;
    @Column(nullable=false) private boolean resolved=false;
    @Column(name="resolved_at") private Instant resolvedAt;
}
