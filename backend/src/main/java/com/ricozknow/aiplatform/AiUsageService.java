package com.ricozknow.aiplatform;

import com.ricozknow.ai.AiChatResponse;
import com.ricozknow.common.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Atomically reserves tenant requests; stores counts only, never prompts or response text. */
@Service @RequiredArgsConstructor
public class AiUsageService {
    private final com.ricozknow.billing.BillingService billing;
    private final JdbcTemplate db;
    @Value("${ricozknow.ai.usage.input-usd-per-1k:0}") private double inputPrice;
    @Value("${ricozknow.ai.usage.output-usd-per-1k:0}") private double outputPrice;
    @Value("${ricozknow.ai.max-requests-per-tenant-month:2000}") private int monthlyLimit;

    public void reserve() {
        UUID tenant=TenantContext.getOrNull();
        if(tenant==null)return; // trusted system jobs may use no request-scoped tenant
        String period=LocalDate.now(ZoneOffset.UTC).toString().substring(0,7);
        int planCap=billing.limit(tenant,"max_ai_requests");
        int allowed=planCap==0?Integer.MAX_VALUE:planCap;
        List<Integer> rows=db.query("""
                INSERT INTO ai_quota_counter(tenant_id,period,requests) VALUES(?,?,1)
                ON CONFLICT(tenant_id,period) DO UPDATE SET requests=ai_quota_counter.requests+1
                WHERE ai_quota_counter.requests < ? RETURNING requests
                """,(rs,i)->rs.getInt(1),tenant,period,allowed);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Monthly AI request quota exceeded");
    }
    public void record(AiChatResponse result,long duration,String operation,boolean success) {
        UUID tenant=TenantContext.getOrNull();
        if(tenant==null)return;
        java.math.BigDecimal cost=(inputPrice>0 || outputPrice>0) && success
                ? java.math.BigDecimal.valueOf((Math.max(0,result.inputTokens())*inputPrice + Math.max(0,result.outputTokens())*outputPrice)/1000.0)
                : null;
        db.update("INSERT INTO ai_usage_event(tenant_id,provider,model,operation,input_tokens,output_tokens,duration_ms,success,estimated_cost_usd) VALUES(?,?,?,?,?,?,?,?,?)",
                tenant,result.provider(),result.model(),operation,Math.max(0,result.inputTokens()),Math.max(0,result.outputTokens()),Math.max(0,duration),success,cost);
    }
    public Map<String,Object> overview() {
        UUID tenant=TenantContext.get();
        List<Map<String,Object>> byProvider=db.queryForList("""
                SELECT provider,model,count(*) AS calls,coalesce(sum(input_tokens),0) AS input_tokens,
                coalesce(sum(output_tokens),0) AS output_tokens,
                coalesce(round(avg(duration_ms)::numeric,0),0) AS average_latency_ms,
                sum(CASE WHEN success THEN 0 ELSE 1 END) AS failures,
                sum(estimated_cost_usd) AS estimated_cost_usd
                FROM ai_usage_event WHERE tenant_id=? GROUP BY provider,model ORDER BY calls DESC
                """,tenant);
        Integer current=db.query("SELECT requests FROM ai_quota_counter WHERE tenant_id=? AND period=?",
                rs->rs.next()?rs.getInt(1):0,tenant,LocalDate.now(ZoneOffset.UTC).toString().substring(0,7));
        return Map.of("quota",billing.limit(tenant,"max_ai_requests"),"used",current==null?0:current,"providers",byProvider,
                "costStatus",inputPrice>0||outputPrice>0?"ESTIMATED_CONFIGURED_PRICE":"NOT_CONFIGURED");
    }
}
