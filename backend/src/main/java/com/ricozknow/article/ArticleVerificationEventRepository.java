package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleVerificationEventRepository extends JpaRepository<ArticleVerificationEvent,UUID> {
 List<ArticleVerificationEvent> findByTenantIdAndArticleIdOrderByCreatedAtDesc(UUID tenantId, UUID articleId);
}
