package com.ricozknow.intelligence;

import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor; import lombok.Setter;
import java.util.UUID;

@Entity @Table(name="article_dependencies") @Getter @Setter @NoArgsConstructor
public class ArticleDependency extends TenantOwnedEntity {
    @Column(name="article_id", nullable=false) private UUID articleId;
    @Column(name="depends_on_article_id", nullable=false) private UUID dependsOnArticleId;
    @Column(name="dependency_type", nullable=false) private String dependencyType = "REFERENCE";
    @Column(length=500) private String evidence;
    @Column(nullable=false) private double confidence;
}
