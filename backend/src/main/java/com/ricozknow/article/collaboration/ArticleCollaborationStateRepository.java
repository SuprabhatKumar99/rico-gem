package com.ricozknow.article.collaboration;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ArticleCollaborationStateRepository extends JpaRepository<ArticleCollaborationState,UUID> {
    Optional<ArticleCollaborationState> findByTenantIdAndVersionId(UUID tenantId, UUID versionId);
}
