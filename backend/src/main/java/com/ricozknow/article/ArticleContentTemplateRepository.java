package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleContentTemplateRepository extends JpaRepository<ArticleContentTemplate,UUID> {
 List<ArticleContentTemplate> findByTenantIdOrderByNameAsc(UUID tenantId);
 Optional<ArticleContentTemplate> findByTenantIdAndId(UUID tenantId, UUID id);
 boolean existsByTenantIdAndTemplateKey(UUID tenantId, String templateKey);
}
