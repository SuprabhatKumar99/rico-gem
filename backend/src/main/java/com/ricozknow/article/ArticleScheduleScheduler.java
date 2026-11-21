package com.ricozknow.article;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component @RequiredArgsConstructor
public class ArticleScheduleScheduler {
 private final TenantRepository tenants; private final ArticleWorkflowService workflow;
 @Scheduled(fixedDelayString="${ricozknow.article.schedule-check-ms:30000}")
 public void run(){for(Tenant t:tenants.findAll()) if(t.getStatus()==Tenant.TenantStatus.ACTIVE) {TenantContext.set(t.getId());try{workflow.runSchedules(t.getId(),Instant.now());}finally{TenantContext.clear();}}}
}
