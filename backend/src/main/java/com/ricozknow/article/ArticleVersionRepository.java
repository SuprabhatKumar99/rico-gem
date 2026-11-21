package com.ricozknow.article;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArticleVersionRepository extends JpaRepository<ArticleVersion, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from ArticleVersion v where v.tenantId = :tenantId and v.id = :id")
    Optional<ArticleVersion> lockForCollaboration(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                                  @org.springframework.data.repository.query.Param("id") UUID id);
    List<ArticleVersion> findByTenantIdAndArticleIdOrderByVersionNumberDesc(UUID tenantId, UUID articleId);
    Optional<ArticleVersion> findByTenantIdAndId(UUID tenantId, UUID id);
    Optional<ArticleVersion> findTopByTenantIdAndArticleIdOrderByVersionNumberDesc(UUID tenantId, UUID articleId);
    List<ArticleVersion> findByTenantIdAndStatusAndExpiresAtBefore(
            UUID tenantId, VersionStatus status, java.time.Instant cutoff);
    List<ArticleVersion> findByTenantIdAndStatusAndExpiresAtBetween(
            UUID tenantId, VersionStatus status, java.time.Instant from, java.time.Instant to);
}
