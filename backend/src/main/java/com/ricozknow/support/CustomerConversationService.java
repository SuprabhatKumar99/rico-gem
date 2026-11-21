package com.ricozknow.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.CustomerAuthService;
import com.ricozknow.customer.CustomerUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class CustomerConversationService {
 private final JdbcTemplate db;
 private final CustomerAuthService auth;
 private final ObjectMapper mapper;
 public CustomerUser customer(String token){CustomerUser customer=auth.current(token);
  if(customer==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Customer sign-in required");return customer;}
 @Transactional
 public UUID saveExchange(String token,UUID existing,String language,String question,String answer,List<String> citations,boolean grounded) {
  CustomerUser customer=auth.current(token);if(customer==null)return null; // anonymous answers are not persisted
  UUID tenant=TenantContext.get();UUID id=existing;
  if(id==null){ id=UUID.randomUUID();db.update("insert into support_conversations(id,tenant_id,customer_id,language) values(?,?,?,?)",id,tenant,customer.getId(),safeLanguage(language)); }
  else if(db.queryForObject("select count(*) from support_conversations where tenant_id=? and customer_id=? and id=?",Integer.class,tenant,customer.getId(),id)!=1)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Conversation not found");
  append(tenant,id,"USER",question,List.of(),false);
  append(tenant,id,"ASSISTANT",answer,citations,grounded);
  db.update("update support_conversations set updated_at=now() where id=? and tenant_id=? and customer_id=?",id,tenant,customer.getId());
  return id;
 }
 private String safeLanguage(String l){return l!=null&&l.matches("[a-zA-Z]{2,3}(-[a-zA-Z]{2,4})?")?l.toLowerCase(Locale.ROOT):"en";}
 private void append(UUID tenant,UUID id,String role,String message,List<String> citations,boolean grounded){
  try{db.update("insert into support_conversation_messages(tenant_id,conversation_id,role,content,citation_slugs,answer_grounded) values(?,?,?,?,?::jsonb,?)",
   tenant,id,role,message,mapper.writeValueAsString(citations),grounded);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> list(String token){CustomerUser user=customer(token);return db.queryForList(
   "select id,language,created_at,updated_at,escalated_ticket_id from support_conversations where tenant_id=? and customer_id=? order by updated_at desc limit 50",
   TenantContext.get(),user.getId());}
 @Transactional(readOnly=true)
 public List<Map<String,Object>> messages(String token,UUID id){CustomerUser user=customer(token);
  if(db.queryForObject("select count(*) from support_conversations where tenant_id=? and customer_id=? and id=?",Integer.class,TenantContext.get(),user.getId(),id)!=1)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return db.queryForList("select role,content,citation_slugs::text as citations,answer_grounded,created_at from support_conversation_messages where tenant_id=? and conversation_id=? order by created_at,id limit 300",TenantContext.get(),id);
 }
}
