package com.ricozknow.article;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name="article_related", uniqueConstraints=@UniqueConstraint(name="uq_article_related", columnNames={"tenant_id","article_id","related_article_id"}))
@Getter @Setter @NoArgsConstructor
public class ArticleRelated extends TenantOwnedEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="article_id", nullable=false) private Article article;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="related_article_id", nullable=false) private Article relatedArticle;
}
