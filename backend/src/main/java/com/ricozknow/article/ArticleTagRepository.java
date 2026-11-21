package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleTagRepository extends JpaRepository<ArticleTag,UUID> {
    List<ArticleTag> findByTenantIdAndArticleIdOrderByTagAsc(UUID tenantId, UUID articleId);
    void deleteByTenantIdAndArticleId(UUID tenantId, UUID articleId);
}
