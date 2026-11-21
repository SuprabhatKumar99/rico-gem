package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleReusableBlockRepository extends JpaRepository<ArticleReusableBlock,UUID> {
 List<ArticleReusableBlock> findByTenantIdOrderByNameAsc(UUID tenantId);
 Optional<ArticleReusableBlock> findByTenantIdAndId(UUID tenantId, UUID id);
}
