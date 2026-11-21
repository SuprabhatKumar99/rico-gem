package com.ricozknow.support;
import com.ricozknow.common.TenantContext; import com.ricozknow.customer.*; import com.ricozknow.notification.EmailService; import com.ricozknow.tenant.*; import lombok.RequiredArgsConstructor; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service @RequiredArgsConstructor public class SupportTicketService {
 @Value("${ricozknow.support.fallback-email:}") String fallbackEmail;
 private final SupportTicketRepository repo; private final TenantRepository tenants; private final EmailService email; private final HelpdeskService helpdesk;
 private final CustomerAuthService customerAuth; private final org.springframework.jdbc.core.JdbcTemplate db;
 @Transactional public SupportTicket create(String emailAddress,String subject,String description,String customerSession){return create(emailAddress,subject,description,customerSession,null);}
 @Transactional public SupportTicket create(String emailAddress,String subject,String description,String customerSession,UUID conversationId){
  UUID tenant=TenantContext.get(); Tenant t=tenants.findById(tenant).orElseThrow(); CustomerUser c=null;
  if(customerSession!=null) c=customerAuth.current(customerSession);
  if(c!=null && !c.getEmail().equalsIgnoreCase(emailAddress.trim()))
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Use your signed-in email address");
  String destination=(t.getSupportEmail()!=null&&!t.getSupportEmail().isBlank())?t.getSupportEmail():fallbackEmail;
  if(conversationId!=null){
   if(c==null || db.queryForObject("select count(*) from support_conversations where id=? and tenant_id=? and customer_id=?",Integer.class,conversationId,tenant,c.getId())!=1)
    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Conversation not found");
  }
  SupportTicket x=new SupportTicket();x.setTenantId(tenant);x.setCustomerId(c==null?null:c.getId());x.setEmail(emailAddress.trim().toLowerCase());x.setSubject(subject.trim());x.setDescription(description.trim());x.setConversationId(conversationId);
  x.setProvider(destination==null||destination.isBlank()?"LOCAL":"EMAIL"); x=repo.save(x);
  if(conversationId!=null)db.update("update support_conversations set escalated_ticket_id=? where id=? and tenant_id=? and customer_id=?",x.getId(),conversationId,tenant,c.getId());
  // Never lose the local ticket because an external email/helpdesk is unavailable.
  try{if(destination!=null&&!destination.isBlank())email.send(destination,"Support request: "+subject,description+"\n\nCustomer: "+emailAddress);}
  catch(Exception ignored){ /* local support ticket remains available in staff queue */ }
  var escalated=helpdesk.create(x); if(escalated.isPresent()){var r=escalated.get();x.setProvider(r.provider());x.setExternalTicketId(r.id());x=repo.save(x);} return x;
 }

}