package com.ricozknow.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ArticleIntelligenceRepository extends JpaRepository<ArticleIntelligence, UUID> {
    Optional<ArticleIntelligence> findByTenantIdAndArticleId(UUID tenantId, UUID articleId);
}
