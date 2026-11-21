package com.ricozknow.article;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.UUID;
/** Idempotently marks expired published content due for review. It never publishes autonomously. */
@Component @RequiredArgsConstructor
public class ExpiryScheduler {
 private final TenantRepository tenants;
 private final ArticleVersionRepository versions;
 private final AuditService audit;
 private final TransactionTemplate transactions;
 @Scheduled(cron="${ricozknow.article.expiry-cron:0 0 3 * * *}",zone="UTC")
 public void flagExpiredContent() {
  for(Tenant tenant:tenants.findAll()) {
   if(tenant.getStatus()!=Tenant.TenantStatus.ACTIVE) continue;
   TenantContext.set(tenant.getId());
   try {transactions.executeWithoutResult(tx->flagForTenant(tenant.getId()));}
   finally {TenantContext.clear();}
  }
 }
 void flagForTenant(UUID tenantId) {
  Instant now=Instant.now();
  for(ArticleVersion version:versions.findByTenantIdAndStatusAndExpiresAtBefore(tenantId,VersionStatus.PUBLISHED,now)) {
   Article article=version.getArticle();
   if(article.getStatus()!=ArticleStatus.PUBLISHED || article.getActiveVersion()==null || !article.getActiveVersion().getId().equals(version.getId()))continue;
   if(article.getNextReviewAt()==null || article.getNextReviewAt().isAfter(now)) {
    article.setNextReviewAt(now);
    article.setReviewDueNotifiedAt(null);
    audit.record("ArticleVersion",version.getId(),"CONTENT_EXPIRED_REVIEW_REQUIRED",null,version.getExpiresAt());
   }
  }
 }
}
