package com.ricozknow.article.collaboration;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter @Setter
@Table(name = "article_collaboration_state")
public class ArticleCollaborationState extends TenantOwnedEntity {
    @Column(name="article_id", nullable=false) private UUID articleId;
    @Column(name="version_id", nullable=false) private UUID versionId;
    @Column(name="ydoc_state", nullable=false, columnDefinition="bytea") private byte[] ydocState;
    @Column(name="mirrored_sha256", nullable=false, length=64) private String mirroredSha256;
}
