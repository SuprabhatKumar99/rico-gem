package com.ricozknow.automation;

import com.ricozknow.common.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tenant-scoped automation rule registry. Only allowlisted actions are executable. */
@RestController
@RequestMapping("/api/v1/automation/rules")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeAutomationController {
  private final JdbcTemplate db;
  private final com.fasterxml.jackson.databind.ObjectMapper mapper;
  private final AutomationExecutionService engine;
  public record Rule(UUID id, String name, String triggerType, String actionType, boolean enabled,
                     Instant createdAt, Instant updatedAt, UUID articleIdFilter, int cooldownMinutes, int maxAttempts) {}
  public record Request(@NotBlank @Size(max=120) String name,
                        @NotBlank @Pattern(regexp="ARTICLE_CREATED|ARTICLE_UPDATED|REVIEW_OVERDUE|CONNECTOR_FAILED") String triggerType,
                        @NotBlank @Pattern(regexp="FLAG_FOR_REVIEW|QUEUE_NOTIFICATION|AUDIT_ONLY") String actionType,
                        boolean enabled,
                        UUID articleIdFilter,
                        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10080) Integer cooldownMinutes,
                        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(12) Integer maxAttempts) {}
  private UUID parseArticleId(String json) {try{String value=mapper.readTree(json).path("articleId").asText("");return value.isBlank()?null:UUID.fromString(value);}catch(Exception ignored){return null;}}
  private Rule row(ResultSet rs,int n) throws SQLException {
    return new Rule((UUID)rs.getObject("id"),rs.getString("name"),rs.getString("trigger_type"),
      rs.getString("action_type"),rs.getBoolean("enabled"),rs.getTimestamp("created_at").toInstant(),
      rs.getTimestamp("updated_at").toInstant(),
      parseArticleId(rs.getString("condition_json")),rs.getInt("cooldown_minutes"),rs.getInt("max_attempts"));
  }
  private String conditionJson(Request request){return request.articleIdFilter()==null?"{}":"{\"articleId\":\""+request.articleIdFilter()+"\"}";}
  @GetMapping @Transactional(readOnly=true)
  public List<Rule> list(){return db.query("select * from knowledge_automation_rule where tenant_id=? order by created_at desc",this::row,TenantContext.get());}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Rule create(@RequestBody @Valid Request request){
    UUID id=UUID.randomUUID();
    db.update("insert into knowledge_automation_rule(id,tenant_id,name,trigger_type,action_type,enabled,condition_json,cooldown_minutes,max_attempts) values(?,?,?,?,?,?,?::jsonb,?,?)",
      id,TenantContext.get(),request.name().trim(),request.triggerType(),request.actionType(),request.enabled(),conditionJson(request),
      request.cooldownMinutes()==null?0:request.cooldownMinutes(),request.maxAttempts()==null?5:request.maxAttempts());
    return one(id);
  }
  @GetMapping("/{id}") @Transactional(readOnly=true)
  public Rule one(@PathVariable UUID id){return db.query("select * from knowledge_automation_rule where tenant_id=? and id=?",this::row,TenantContext.get(),id)
    .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
  @PutMapping("/{id}") @Transactional
  public Rule update(@PathVariable UUID id,@RequestBody @Valid Request request){
    int count=db.update("update knowledge_automation_rule set name=?,trigger_type=?,action_type=?,enabled=?,condition_json=?::jsonb,cooldown_minutes=?,max_attempts=?,updated_at=now() where id=? and tenant_id=?",
      request.name().trim(),request.triggerType(),request.actionType(),request.enabled(),conditionJson(request),request.cooldownMinutes()==null?0:request.cooldownMinutes(),
      request.maxAttempts()==null?5:request.maxAttempts(),id,TenantContext.get());
    if(count==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return one(id);
  }
  @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
  public void delete(@PathVariable UUID id){if(db.update("delete from knowledge_automation_rule where id=? and tenant_id=?",id,TenantContext.get())==0)
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
  @GetMapping("/{id}/executions") @Transactional(readOnly=true)
  public List<java.util.Map<String,Object>> executions(@PathVariable UUID id){one(id);return engine.history(id);}
  @PostMapping("/{id}/executions/{executionId}/retry")
  public void retry(@PathVariable UUID id,@PathVariable UUID executionId){one(id);engine.retry(id,executionId);}
  /** Safe dry-run: no external effects or writes. */
  public record Preview(UUID ruleId,String triggerType,String actionType,String explanation,boolean wouldExecute){}
  @PostMapping("/{id}/preview") @Transactional(readOnly=true)
  public Preview preview(@PathVariable UUID id){Rule rule=one(id);return new Preview(rule.id(),rule.triggerType(),rule.actionType(),
    "Dry-run only. Enabled rules execute when matching trusted events occur; this request has no side effects.",rule.enabled());}
}
