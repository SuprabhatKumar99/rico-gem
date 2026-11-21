package com.ricozknow.notification;
import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.tenant.Tenant;
import com.ricozknow.tenant.TenantRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Component @RequiredArgsConstructor
public class ReviewDigestScheduler {
 private final TenantRepository tenants; private final ArticleRepository articles; private final UserRepository users; private final EmailService email;
 @Scheduled(cron="0 0 8 * * *")
 public void sendDigest(){
  for(Tenant t:tenants.findAll()) if(t.getStatus()==Tenant.TenantStatus.ACTIVE) sendForTenant(t.getId());
 }
 @Transactional
 void sendForTenant(UUID tenant){
  TenantContext.set(tenant);
  try{
   long count=articles.findByTenantIdAndStatus(tenant,ArticleStatus.IN_REVIEW,org.springframework.data.domain.PageRequest.of(0,1)).getTotalElements();
   if(count==0)return;
   for(User u:users.findAll()) if(tenant.equals(u.getTenantId()) && (has(u,"ADMIN")||has(u,"REVIEWER")) && u.isEmailVerified())
      email.send(u.getEmail(),"RicozKnow review digest",count+" article"+(count==1?" is":"s are")+" awaiting review in RicozKnow.");
  } finally {TenantContext.clear();}
 }
 private boolean has(User u,String role){return u.getRoles().stream().map(Role::getName).anyMatch(role::equals);}
}
