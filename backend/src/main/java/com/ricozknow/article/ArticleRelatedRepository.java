package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleRelatedRepository extends JpaRepository<ArticleRelated,UUID> {
    List<ArticleRelated> findByTenantIdAndArticleId(UUID tenantId, UUID articleId);
    void deleteByTenantIdAndArticleId(UUID tenantId, UUID articleId);
}
