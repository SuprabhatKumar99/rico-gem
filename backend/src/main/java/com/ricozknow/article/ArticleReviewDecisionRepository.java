package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleReviewDecisionRepository extends JpaRepository<ArticleReviewDecision,UUID> {
 List<ArticleReviewDecision> findByTenantIdAndArticleIdOrderByCreatedAtDesc(UUID tenantId, UUID articleId);
}
