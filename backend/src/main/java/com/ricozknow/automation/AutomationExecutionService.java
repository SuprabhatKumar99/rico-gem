package com.ricozknow.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import com.ricozknow.notification.ContentNotificationService;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.user.UserRepository;
import com.ricozknow.user.User;
import com.ricozknow.role.Role;
import com.ricozknow.notification.InAppNotification;
import com.ricozknow.notification.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.dao.DuplicateKeyException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AutomationExecutionService {
 private final JdbcTemplate db;
 private final org.springframework.transaction.support.TransactionTemplate transactions;
 private final ObjectMapper mapper;
 private final ArticleRepository articles;
 private final UserRepository users;
 private final InAppNotificationRepository notifications;

 public record Execution(UUID id,UUID tenantId,UUID ruleId,UUID articleId,String eventKey,String action,String state,int attempts,int maxAttempts) {}
 private Execution map(ResultSet r,int i)throws SQLException {
  return new Execution((UUID)r.getObject("id"),(UUID)r.getObject("tenant_id"),(UUID)r.getObject("rule_id"),
    (UUID)r.getObject("target_article_id"),r.getString("event_key"),r.getString("action_type"),r.getString("state"),r.getInt("attempts"),r.getInt("max_attempts"));
 }
 /** Enqueue after the article transaction commits. The originating tenant is explicit, not inferred from a worker thread. */
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
 public void onArticleEvent(AutomationEvent event) {
  TenantContext.set(event.tenantId());
  try { enqueue(event); } finally {TenantContext.clear();}
 }
 @Transactional
 public int enqueue(AutomationEvent event) {
  if(!Set.of("ARTICLE_CREATED","ARTICLE_UPDATED","REVIEW_OVERDUE","CONNECTOR_FAILED").contains(event.type()))throw new IllegalArgumentException("Unknown trigger");
  UUID tenant=TenantContext.get();
  if(!tenant.equals(event.tenantId()))throw new SecurityException("Wrong tenant");
  int created=0;
  for(var rule:db.queryForList("select id,action_type,max_attempts,cooldown_minutes,condition_json::text condition_json from knowledge_automation_rule where tenant_id=? and enabled=true and trigger_type=?",tenant,event.type())){
   UUID ruleId=(UUID)rule.get("id");
   if(!matches((String)rule.get("condition_json"),event.articleId()))continue;
   int cooldown=((Number)rule.get("cooldown_minutes")).intValue();
   if(cooldown>0 && db.queryForObject("select count(*) from knowledge_automation_execution where tenant_id=? and rule_id=? and created_at>now()-(? * interval '1 minute')",Integer.class,tenant,ruleId,cooldown)>0)continue;
   try {
    created+=db.update("insert into knowledge_automation_execution(tenant_id,rule_id,event_key,trigger_type,action_type,target_article_id,max_attempts) values(?,?,?,?,?,?,?) on conflict (tenant_id,rule_id,event_key) do nothing",
     tenant,ruleId,event.eventKey(),event.type(),rule.get("action_type"),event.articleId(),rule.get("max_attempts"));
   } catch(DuplicateKeyException ignored) { /* idempotent event replay */ }
  }
  return created;
 }
 private boolean matches(String raw,UUID articleId) {
  try {JsonNode n=mapper.readTree(raw);if(n.hasNonNull("articleId"))return articleId!=null&&articleId.toString().equals(n.path("articleId").asText());return true;}
  catch(Exception ignored){return false;}
 }
 /** Claims a batch atomically across workers. A crashed worker's expired lease is reclaimed. */
 @Transactional
 public List<Execution> claim() {
  db.update("update knowledge_automation_execution set state='DEAD',finished_at=now(),locked_at=null,last_error='Worker lease expired after final attempt' where state='RUNNING' and locked_at<now()-interval '5 minutes' and attempts>=max_attempts");
  return db.query("""
    with due as (select e.id from knowledge_automation_execution e join knowledge_automation_rule r on r.id=e.rule_id and r.tenant_id=e.tenant_id
       where r.enabled=true and e.attempts<e.max_attempts and ((e.state='PENDING' and e.next_attempt_at<=now()) or (e.state='RUNNING' and e.locked_at<now()-interval '5 minutes'))
       order by e.next_attempt_at,e.created_at for update of e skip locked limit 20)
    update knowledge_automation_execution e set state='RUNNING',attempts=e.attempts+1,locked_at=now()
    from due where e.id=due.id returning e.*
  """,this::map);
 }
 @Scheduled(fixedDelayString="${RICOZ_AUTOMATION_WORKER_DELAY_MS:15000}")
 public void executeDue() {
  for(Execution execution:claim()){
   TenantContext.set(execution.tenantId());
   try{transactions.executeWithoutResult(status -> run(execution));}
   catch(Exception e){transactions.executeWithoutResult(status -> failure(execution,e));}
   finally{TenantContext.clear();}
  }
 }
 @Transactional
 public void run(Execution e) {
  UUID tenant=TenantContext.get();if(!tenant.equals(e.tenantId()))throw new SecurityException("Tenant mismatch");
  // Recheck rule and executable state on every attempt.
  Boolean active=db.query("select enabled from knowledge_automation_rule where id=? and tenant_id=?",rs->rs.next()?rs.getBoolean(1):null,e.ruleId(),tenant);
  if(!Boolean.TRUE.equals(active))throw new IllegalStateException("Rule disabled or deleted");
  switch(e.action()) {
   case "AUDIT_ONLY" -> { /* durable execution-step row is the audit record */ }
   case "FLAG_FOR_REVIEW" -> {
    if(e.articleId()==null)throw new IllegalArgumentException("Article required for flagging");
    if(db.update("update articles set next_review_at=least(coalesce(next_review_at,now()),now()) where tenant_id=? and id=?",tenant,e.articleId())==0)
      throw new IllegalArgumentException("Article not found in tenant");
   }
   case "QUEUE_NOTIFICATION" -> {
    // Only admins and reviewers of this tenant. Never expand rule JSON to arbitrary recipients.
    for(User user:users.findByTenantId(tenant,org.springframework.data.domain.PageRequest.of(0,1000)).getContent()){
     if(user.getStatus()!=User.UserStatus.ACTIVE||user.getRoles().stream().map(Role::getName).noneMatch(s->s.equals("ADMIN")||s.equals("REVIEWER")))continue;
     InAppNotification n=new InAppNotification();n.setTenantId(tenant);n.setUserId(user.getId());n.setType("AUTOMATION");n.setTitle("Knowledge automation alert");n.setMessage("Rule triggered: "+e.eventKey());
     if(e.articleId()!=null && articles.findByTenantIdAndId(tenant,e.articleId()).isPresent())n.setArticleId(e.articleId());
     notifications.save(n);
    }
   }
   default -> throw new IllegalStateException("Unsupported action");
  }
  db.update("insert into knowledge_automation_execution_step(tenant_id,execution_id,attempt,state,detail) values(?,?,?,'SUCCEEDED','Completed')",tenant,e.id(),e.attempts());
  db.update("update knowledge_automation_execution set state='SUCCEEDED',finished_at=now(),locked_at=null,last_error=null where tenant_id=? and id=? and state='RUNNING'",tenant,e.id());
 }
 @Transactional
 public void failure(Execution e,Exception error) {
  UUID tenant=TenantContext.get();
  String safe=error.getClass().getSimpleName();
  String next=e.attempts()>=e.maxAttempts()?"DEAD":"PENDING";
  // Exponential delay capped at an hour; poison messages are stored as DEAD for inspection.
  int delay=Math.min(3600, (int)Math.pow(2,Math.min(e.attempts(),10))*15);
  db.update("update knowledge_automation_execution set state=?,next_attempt_at=now()+(? * interval '1 second'),locked_at=null,last_error=?,finished_at=case when ?='DEAD' then now() else null end where tenant_id=? and id=?",
   next,delay,safe,next,tenant,e.id());
  db.update("insert into knowledge_automation_execution_step(tenant_id,execution_id,attempt,state,detail) values(?,?,?,?,?)",tenant,e.id(),e.attempts(),next,safe);
 }
 @Scheduled(fixedDelayString="${RICOZ_AUTOMATION_TRIGGER_DELAY_MS:60000}")
 public void scanTriggers(){
  for(UUID tenant:db.queryForList("select id from tenants where status='ACTIVE'",UUID.class)){
   TenantContext.set(tenant);
   try{
    String day=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    for(UUID id:db.queryForList("select id from articles where tenant_id=? and status='PUBLISHED' and next_review_at<now() order by next_review_at limit 100",UUID.class,tenant))
      enqueue(new AutomationEvent(tenant,"REVIEW_OVERDUE",id,"overdue:"+id+":"+day));
    for(UUID id:db.queryForList("select id from connector_sync_run where tenant_id=? and state='FAILED' and started_at>now()-interval '7 days' order by started_at desc limit 100",UUID.class,tenant))
      enqueue(new AutomationEvent(tenant,"CONNECTOR_FAILED",null,"connector-failed:"+id));
   }catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Automation trigger scan failed for tenant {}",tenant,e);}
   finally{TenantContext.clear();}
  }
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> history(UUID ruleId) {
  UUID tenant=TenantContext.get();
  return db.queryForList("select id,event_key,action_type,state,attempts,max_attempts,last_error,created_at,finished_at from knowledge_automation_execution where tenant_id=? and rule_id=? order by created_at desc limit 100",tenant,ruleId);
 }
 @Transactional
 public void retry(UUID ruleId,UUID id) {
  if(db.update("update knowledge_automation_execution set state='PENDING',attempts=0,next_attempt_at=now(),last_error=null,finished_at=null where id=? and rule_id=? and tenant_id=? and state='DEAD'",id,ruleId,TenantContext.get())!=1)
   throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
 }
}
