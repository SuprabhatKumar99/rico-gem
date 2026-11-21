package com.ricozknow.article;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="article_tags", uniqueConstraints=@UniqueConstraint(name="uq_article_tag", columnNames={"tenant_id","article_id","tag"}))
@Getter @Setter @NoArgsConstructor
public class ArticleTag extends TenantOwnedEntity {
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="article_id", nullable=false)
    private Article article;
    @Column(nullable=false, length=80) private String tag;
}
