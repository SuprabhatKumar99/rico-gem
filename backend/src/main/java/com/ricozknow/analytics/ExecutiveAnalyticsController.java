package com.ricozknow.analytics;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

/** Phase 7 operational analytics. Each aggregate is tenant-filtered and clearly labels estimates/proxies. */
@RestController @RequestMapping("/api/v1/analytics/executive")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class ExecutiveAnalyticsController {
    private final JdbcTemplate db;
    private static int window(int days){
        if(days!=7 && days!=30 && days!=90 && days!=365)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a 7/30/90/365-day window");
        return days;
    }
    private long number(String sql,Object... args){
        Long v=db.queryForObject(sql,Long.class,args);return v==null?0:v;
    }
    @GetMapping @Transactional(readOnly=true)
    public Map<String,Object> overview(@RequestParam(defaultValue="30") int days){
        int d=window(days);UUID t=TenantContext.get();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("days",d);
        result.put("knowledge",Map.of(
            "articleViews",number("select coalesce(sum(views),0) from analytics_daily_article_stats where tenant_id=? and stat_date>=current_date-?",t,d),
            "helpful",number("select coalesce(sum(helpful_count),0) from analytics_daily_article_stats where tenant_id=? and stat_date>=current_date-?",t,d),
            "unhelpful",number("select coalesce(sum(unhelpful_count),0) from analytics_daily_article_stats where tenant_id=? and stat_date>=current_date-?",t,d),
            "published",number("select count(*) from articles where tenant_id=? and status='PUBLISHED'",t),
            "unverified",number("select count(*) from articles where tenant_id=? and status='PUBLISHED' and verified_at is null",t)));
        result.put("search",Map.of(
            "queries",number("select coalesce(sum(volume_count),0) from analytics_daily_search_stats where tenant_id=? and stat_date>=current_date-?",t,d),
            "zeroResults",number("select coalesce(sum(zero_result_count),0) from analytics_daily_search_stats where tenant_id=? and stat_date>=current_date-?",t,d),
            "unsuccessful",number("select coalesce(sum(unsuccessful_count),0) from analytics_daily_search_stats where tenant_id=? and stat_date>=current_date-?",t,d)));
        result.put("ai",Map.of(
            "requests",number("select count(*) from ai_usage_event where tenant_id=? and created_at>=now()-(?*interval '1 day')",t,d),
            "failures",number("select count(*) from ai_usage_event where tenant_id=? and not success and created_at>=now()-(?*interval '1 day')",t,d),
            "evaluationHits",number("select count(*) from ai_eval_result where tenant_id=? and hit and created_at>=now()-(?*interval '1 day')",t,d),
            "evaluated",number("select count(*) from ai_eval_result where tenant_id=? and created_at>=now()-(?*interval '1 day')",t,d)));
        result.put("connectors",Map.of(
            "sources",number("select count(*) from connector_source where tenant_id=? and not disabled",t),
            "failures",number("select count(*) from connector_sync_run where tenant_id=? and state='FAILED' and started_at>=now()-(?*interval '1 day')",t,d),
            "completed",number("select count(*) from connector_sync_run where tenant_id=? and state='SUCCEEDED' and started_at>=now()-(?*interval '1 day')",t,d)));
        result.put("support",Map.of(
            "tickets",number("select count(*) from support_tickets where tenant_id=? and created_at>=now()-(?*interval '1 day')",t,d),
            "resolved",number("select count(*) from support_tickets where tenant_id=? and resolved_at>=now()-(?*interval '1 day')",t,d),
            "estimatedDeflection",number("select coalesce(sum(estimated_deflection_count),0) from analytics_daily_deflection_stats where tenant_id=? and stat_date>=current_date-?",t,d)));
        result.put("productivity",Map.of(
            "newArticles",number("select count(*) from articles where tenant_id=? and created_at>=now()-(?*interval '1 day')",t,d),
            "activeStaff",number("select count(distinct user_id) from analytics_events where tenant_id=? and user_id is not null and occurred_at>=now()-(?*interval '1 day')",t,d),
            "staffSeats",number("select count(*) from users where tenant_id=? and status <> 'DISABLED'",t)));
        result.put("providerCosts",db.queryForList("""
            select provider,model,count(*) as requests,
              coalesce(sum(input_tokens),0) as input_tokens,coalesce(sum(output_tokens),0) as output_tokens,
              round(avg(duration_ms)::numeric,0) as latency_ms,sum(estimated_cost_usd) as estimated_cost_usd
            from ai_usage_event where tenant_id=? and created_at>=now()-(?*interval '1 day')
            group by provider,model order by requests desc limit 30
            """,t,d));
        result.put("subscription",db.queryForList("select s.plan_code,s.status,s.trial_ends_at,s.current_period_end from saas_subscription s where tenant_id=?",t));
        result.put("paidInvoicesMinor",number("select coalesce(sum(amount_paid_minor),0) from saas_invoice where tenant_id=? and paid_at>=now()-(?*interval '1 day') and currency='usd'",t,d));
        result.put("labels",Map.of("deflection","ESTIMATED_NOT_CAUSAL","aiQuality","RETRIEVAL_EVAL_PROXY","aiCost","ESTIMATED_IF_CONFIGURED","revenue","RECONCILED_INVOICE_USD_CENTS"));
        return result;
    }
    @GetMapping("/daily") @Transactional(readOnly=true)
    public List<Map<String,Object>> daily(@RequestParam(defaultValue="30") int days){
        int d=window(days);UUID t=TenantContext.get();
        return db.queryForList("""
            select stat_date as day,coalesce(sum(views),0) as article_views,
              coalesce(sum(helpful_count),0) as helpful
            from analytics_daily_article_stats where tenant_id=? and stat_date>=current_date-?
            group by stat_date order by stat_date
            """,t,d);
    }
}
