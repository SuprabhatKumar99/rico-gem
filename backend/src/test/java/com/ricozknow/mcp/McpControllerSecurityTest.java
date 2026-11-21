package com.ricozknow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.RateLimiter;
import com.ricozknow.rag.RagApiPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class McpControllerSecurityTest {
    private final ObjectMapper json=new ObjectMapper();
    private final McpAuthorization auth=mock(McpAuthorization.class);
    private final McpKnowledgeTools knowledge=mock(McpKnowledgeTools.class);
    private final McpUsageService usage=mock(McpUsageService.class);
    private final RateLimiter limiter=mock(RateLimiter.class);
    private final HttpServletRequest request=mock(HttpServletRequest.class);
    private McpController subject() {
        var ctrl=new McpController(auth,knowledge,usage,limiter,json);
        ReflectionTestUtils.setField(ctrl,"enabled",true);
        ReflectionTestUtils.setField(ctrl,"writesEnabled",false);
        ReflectionTestUtils.setField(ctrl,"requestsPerMinute",60);
        ReflectionTestUtils.setField(ctrl,"allowedOrigins","");
        when(auth.principal()).thenReturn(new RagApiPrincipal(UUID.randomUUID(),UUID.randomUUID(),Set.of("MCP_CONNECT","MCP_READ","MCP_SEARCH","MCP_WRITE")));
        return ctrl;
    }
    @Test void rejectsUntrustedBrowserOrigin() throws Exception {
        var ctrl=subject();when(request.getHeader("Origin")).thenReturn("https://evil.example");
        var jsonBody=json.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertThatThrownBy(()->ctrl.post(jsonBody,request,"2026-07-28","tools/list",null)).isInstanceOf(Exception.class);
        verifyNoInteractions(knowledge,usage);
    }
    @Test void refusesHeaderBodyMismatchBeforeToolExecution() throws Exception {
        var ctrl=subject();var msg=json.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        var response=ctrl.post(msg,request,"2026-07-28","tools/call",null);
        assertThat(response.getBody().get("error")).isNotNull();verifyNoInteractions(knowledge,usage);
    }
    @Test void disabledWriteToolIsNotExposed() throws Exception {
        var ctrl=subject();when(limiter.allow(anyString(),anyInt(),any(Duration.class))).thenReturn(true);
        var msg=json.readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        var response=ctrl.post(msg,request,"2026-07-28","tools/list",null);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().toString()).contains("knowledge_search","knowledge_get").doesNotContain("request_article_review");
    }
}
