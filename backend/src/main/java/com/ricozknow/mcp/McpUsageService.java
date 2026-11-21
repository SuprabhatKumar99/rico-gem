package com.ricozknow.mcp;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class McpUsageService {
    private final JdbcTemplate jdbc;

    @Transactional
    public void record(UUID keyId, String method, String tool, boolean success, long latencyMs) {
        jdbc.update("insert into mcp_api_usage (id,tenant_id,api_key_id,method,tool_name,success,latency_ms) values (?,?,?,?,?,?,?)",
                UUID.randomUUID(), TenantContext.get(), keyId, method, tool, success, Math.max(0,latencyMs));
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> summary() {
        return jdbc.queryForList("select api_key_id,method,coalesce(tool_name,'') as tool_name,count(*) as requests, " +
                "count(*) filter (where not success) as failures,round(avg(latency_ms)) as average_latency_ms " +
                "from mcp_api_usage where tenant_id=? and created_at >= now()-interval '30 days' " +
                "group by api_key_id,method,tool_name order by requests desc limit 100",TenantContext.get());
    }
}
