package com.ricozknow.article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ArticleBlockCommentRepository extends JpaRepository<ArticleBlockComment,UUID> {
 List<ArticleBlockComment> findByTenantIdAndArticleIdAndVersionIdOrderByCreatedAtAsc(UUID tenantId,UUID articleId,UUID versionId);
 boolean existsByTenantIdAndVersionIdAndResolvedFalse(UUID tenantId,UUID versionId);
 Optional<ArticleBlockComment> findByTenantIdAndId(UUID tenantId,UUID id);
}
