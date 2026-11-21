package com.ricozknow.support;

import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.InAppNotification;
import com.ricozknow.notification.InAppNotificationRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import com.ricozknow.role.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

@Service @RequiredArgsConstructor
public class SupportEscalationWorker {
 private final JdbcTemplate db;
 private final TransactionTemplate tx;
 private final UserRepository users;
 private final InAppNotificationRepository notifications;
 @Value("${RICOZ_SUPPORT_ESCALATION_HOURS:24}") private int hours;
 @Scheduled(fixedDelayString="${RICOZ_SUPPORT_ESCALATION_SCAN_MS:3600000}")
 public void scan(){
  if(hours<1||hours>720)return;
  for(UUID tenant:db.queryForList("select id from tenants where status='ACTIVE'",UUID.class)){
   TenantContext.set(tenant);
   try{
    var due=db.queryForList("select id from support_tickets where tenant_id=? and status='OPEN' and escalated_at is null and created_at<now()-(? * interval '1 hour') order by created_at limit 100",UUID.class,tenant,hours);
    for(UUID id:due){try{tx.executeWithoutResult(st -> escalate(tenant,id));}catch(Exception ex){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Support escalation failed for ticket {}",id,ex);}}
   }finally{TenantContext.clear();}
  }
 }
 private void escalate(UUID tenant,UUID ticket){
  if(db.update("update support_tickets set escalated_at=now(),updated_at=now() where id=? and tenant_id=? and status='OPEN' and escalated_at is null",ticket,tenant)!=1)return;
  for(User user:users.findByTenantId(tenant,PageRequest.of(0,1000)).getContent()){
   if(user.getStatus()!=User.UserStatus.ACTIVE||user.getRoles().stream().map(Role::getName).noneMatch("ADMIN"::equals))continue;
   InAppNotification note=new InAppNotification();note.setTenantId(tenant);note.setUserId(user.getId());note.setType("SUPPORT_ESCALATION");
   note.setTitle("Support ticket requires attention");note.setMessage("Ticket "+ticket+" is still open after its escalation threshold.");notifications.save(note);
  }
 }
}
