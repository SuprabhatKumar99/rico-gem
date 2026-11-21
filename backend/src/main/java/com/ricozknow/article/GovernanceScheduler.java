package com.ricozknow.article;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class GovernanceScheduler {
 private final TenantRepository tenants;
 private final ArticleGovernanceService governance;
 @Scheduled(cron="${ricozknow.article.governance-cron:0 15 3 * * *}",zone="UTC")
 public void flagReviews() {
  for(Tenant tenant:tenants.findAll()) {
   if(tenant.getStatus()!=Tenant.TenantStatus.ACTIVE) continue;
   TenantContext.set(tenant.getId());
   try {governance.signalDueContent();} finally {TenantContext.clear();}
  }
 }
}
