package com.ricozknow.mcp;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Staff-only monitoring and human resolution of automated review requests. */
@RestController @RequestMapping("/api/v1/admin/developer") @PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class McpAdminController {
    private final McpUsageService usage;
    private final JdbcTemplate jdbc;
    private final com.ricozknow.audit.AuditService audit;

    @GetMapping("/usage") public List<Map<String,Object>> usage(){return usage.summary();}
    @GetMapping("/review-requests") public List<Map<String,Object>> reviewRequests(){
        return jdbc.queryForList("select id,article_id,api_key_id,reason,status,created_at,resolved_at " +
                "from mcp_review_requests where tenant_id=? order by created_at desc limit 100",TenantContext.get());
    }
    @PostMapping("/review-requests/{id}/resolve") public void resolve(@PathVariable UUID id){
        int changed=jdbc.update("update mcp_review_requests set status='RESOLVED',resolved_at=now() " +
                "where tenant_id=? and id=? and status='OPEN'",TenantContext.get(),id);
        if(changed==1) audit.record("McpReviewRequest",id,"MCP_REVIEW_RESOLVED",null,Map.of("requestId",id.toString()));
        if(changed!=1)throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,"Open request not found");
    }
}
