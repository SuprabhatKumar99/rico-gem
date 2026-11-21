package com.ricozknow.mcp;

import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagApiPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class McpAuthorizationTest {
    private final McpAuthorization access=new McpAuthorization();
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}
    private void authenticate(UUID tenant,String...scopes){
        var principal=new RagApiPrincipal(UUID.randomUUID(),tenant,Set.of(scopes));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }
    @Test void deniesJwtAndMissingMcpConnect() {
        UUID tenant=UUID.randomUUID();TenantContext.set(tenant);
        assertThatThrownBy(access::principal).isInstanceOf(Exception.class);
        authenticate(tenant,"RAG_SEARCH","MCP_READ");
        assertThatThrownBy(access::principal).isInstanceOf(Exception.class);
    }
    @Test void deniesKeyForDifferentTenantEvenWithScope() {
        TenantContext.set(UUID.randomUUID());authenticate(UUID.randomUUID(),"MCP_CONNECT","MCP_SEARCH");
        assertThatThrownBy(()->access.requireScope("MCP_SEARCH")).isInstanceOf(Exception.class);
    }
    @Test void requiresExactScopeAndTenant(){
        UUID tenant=UUID.randomUUID();TenantContext.set(tenant);authenticate(tenant,"MCP_CONNECT","MCP_READ");
        assertThat(access.requireScope("MCP_READ")).isNotNull();
        assertThatThrownBy(()->access.requireScope("MCP_WRITE")).isInstanceOf(Exception.class);
    }
}
