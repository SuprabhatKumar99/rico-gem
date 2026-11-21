package com.ricozknow.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ArticleDependencyRepository extends JpaRepository<ArticleDependency, UUID> {
    List<ArticleDependency> findByTenantIdAndArticleId(UUID tenantId, UUID articleId);
    List<ArticleDependency> findByTenantIdAndDependsOnArticleId(UUID tenantId, UUID articleId);
    void deleteByTenantIdAndArticleId(UUID tenantId, UUID articleId);
}
