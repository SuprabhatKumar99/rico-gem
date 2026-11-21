package com.ricozknow.search;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IndexStatusService {
    private final ArticleRepository articleRepository;

    @Transactional
    public void searchProcessing(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            a.setSearchIndexStatus(IndexStatus.PROCESSING);
            a.setSearchIndexError(null);
        });
    }

    @Transactional
    public void ragProcessing(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            a.setRagIndexStatus(IndexStatus.PROCESSING);
            a.setRagIndexError(null);
        });
    }

    @Transactional
    public void searchIndexed(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            a.setSearchIndexStatus(IndexStatus.INDEXED);
            a.setSearchIndexedAt(Instant.now());
            a.setSearchIndexError(null);
        });
    }

    @Transactional
    public void ragIndexed(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            a.setRagIndexStatus(IndexStatus.INDEXED);
            a.setRagIndexedAt(Instant.now());
            a.setRagIndexError(null);
        });
    }

    @Transactional
    public void searchFailed(UUID tenantId, UUID articleId, Throwable error) {
        updateError(tenantId, articleId, true, error);
    }

    @Transactional
    public void ragFailed(UUID tenantId, UUID articleId, Throwable error) {
        updateError(tenantId, articleId, false, error);
    }

    @Transactional
    public void markSkipped(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            a.setSearchIndexStatus(IndexStatus.SKIPPED);
            a.setRagIndexStatus(IndexStatus.SKIPPED);
        });
    }

    @Transactional
    public void markRagSkipped(UUID tenantId, UUID articleId) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a ->
                a.setRagIndexStatus(IndexStatus.SKIPPED));
    }

    private void updateError(UUID tenantId, UUID articleId, boolean search, Throwable error) {
        articleRepository.findByTenantIdAndId(tenantId, articleId).ifPresent(a -> {
            String msg = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            msg = msg.length() > 4000 ? msg.substring(0, 4000) : msg;
            if (search) {
                a.setSearchIndexStatus(IndexStatus.FAILED);
                a.setSearchIndexError(msg);
            } else {
                a.setRagIndexStatus(IndexStatus.FAILED);
                a.setRagIndexError(msg);
            }
        });
    }
}
