package com.ricozknow.support;

import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.CustomerAuthService;
import com.ricozknow.customer.CustomerUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

@RestController @RequiredArgsConstructor
public class SupportManagementController {
 private final JdbcTemplate db;
 private final CustomerAuthService auth;
 @GetMapping("/api/v1/admin/support/tickets") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
 public List<Map<String,Object>> tickets(){return db.queryForList("select id,customer_id,email,subject,status,provider,external_ticket_id,created_at,resolved_at,escalated_at from support_tickets where tenant_id=? order by created_at desc limit 100",TenantContext.get());}
 @PatchMapping("/api/v1/admin/support/tickets/{id}") @PreAuthorize("hasRole('ADMIN')") @Transactional
 public void status(@PathVariable UUID id,@Valid @RequestBody StatusUpdate update){
  if(db.update("update support_tickets set status=?,resolved_at=case when ? in ('RESOLVED','CLOSED') then now() else null end,updated_at=now() where tenant_id=? and id=?",
   update.status(),update.status(),TenantContext.get(),id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
 }
 public record StatusUpdate(@jakarta.validation.constraints.NotNull @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status){}
 @GetMapping("/api/v1/admin/support/insights") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
 public Map<String,Object> insights(){UUID tenant=TenantContext.get();
  Long tickets=db.queryForObject("select count(*) from support_tickets where tenant_id=?",Long.class,tenant);
  Long resolved=db.queryForObject("select count(*) from support_tickets where tenant_id=? and status in ('RESOLVED','CLOSED')",Long.class,tenant);
  Long conversations=db.queryForObject("select count(*) from support_conversations where tenant_id=?",Long.class,tenant);
  Long answered=db.queryForObject("select count(distinct conversation_id) from support_conversation_messages where tenant_id=? and role='ASSISTANT' and answer_grounded=true",Long.class,tenant);
  Long escalated=db.queryForObject("select count(distinct conversation_id) from support_tickets where tenant_id=? and conversation_id is not null",Long.class,tenant);
  long eligible=Math.max(0,answered==null?0:answered),handed=Math.max(0,escalated==null?0:escalated);
  // Proxy, not proof of avoided tickets: customers may call/email outside the portal.
  return Map.of("tickets",tickets,"resolvedTickets",resolved,"conversations",conversations,"groundedConversations",eligible,
   "escalatedConversations",handed,"estimatedDeflectionRate",eligible==0?0.0:Math.max(0d,(eligible-handed)/(double)eligible));
 }
 @GetMapping("/api/v1/portal/support/my-tickets") @Transactional(readOnly=true)
 public List<Map<String,Object>> mine(@CookieValue(name="rk_customer_session",required=false) String token){CustomerUser u=auth.current(token);
  if(u==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  return db.queryForList("select id,subject,status,provider,created_at,resolved_at from support_tickets where tenant_id=? and customer_id=? order by created_at desc limit 100",TenantContext.get(),u.getId());
 }
}
