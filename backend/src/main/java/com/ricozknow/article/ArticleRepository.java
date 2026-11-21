package com.ricozknow.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

    @EntityGraph(attributePaths = {"activeVersion"})
    Optional<Article> findByTenantIdAndId(UUID tenantId, UUID id);

    @EntityGraph(attributePaths = {"activeVersion"})
    Optional<Article> findByTenantIdAndSlugAndStatus(UUID tenantId, String slug, ArticleStatus status);
    
    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantId(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatus(UUID tenantId, ArticleStatus status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibility(UUID tenantId, ArticleStatus status, Visibility visibility, Pageable pageable);
   
    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityAndCategoryId(
            UUID tenantId, ArticleStatus status, Visibility visibility, UUID categoryId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityIn(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, Pageable pageable);
    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityInAndCategoryId(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, UUID categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityInAndPinnedTrue(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, Pageable pageable);

    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityInAndFeaturedTrue(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, Pageable pageable);

    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityInAndCategoryIdAndPinnedTrue(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, UUID categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"activeVersion"})
    Page<Article> findByTenantIdAndStatusAndVisibilityInAndCategoryIdAndFeaturedTrue(UUID tenantId, ArticleStatus status, java.util.Collection<Visibility> visibility, UUID categoryId, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from Article a where a.tenantId = :tenantId and a.id = :articleId")
    Optional<Article> lockForScheduledOperation(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
                                                 @org.springframework.data.repository.query.Param("articleId") UUID articleId);

    boolean existsByTenantIdAndSlug(UUID tenantId, String slug);

    List<Article> findAllByTenantId(UUID tenantId);
}
