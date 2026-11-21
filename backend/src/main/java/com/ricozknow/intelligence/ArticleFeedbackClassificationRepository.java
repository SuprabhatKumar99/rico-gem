package com.ricozknow.intelligence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ArticleFeedbackClassificationRepository extends JpaRepository<ArticleFeedbackClassification, UUID> {
    boolean existsByAnalyticsEventId(UUID eventId);
    List<ArticleFeedbackClassification> findByTenantIdAndArticleId(UUID tenantId, UUID articleId);
}
