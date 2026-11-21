package com.ricozknow.mcp;

import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagApiPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Machine identities are isolated from staff sessions and ALWAYS tenant-bound. */
@Component
public class McpAuthorization {
    public RagApiPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof RagApiPrincipal principal)
                || !principal.tenantId().equals(TenantContext.get())
                || !principal.scopes().contains("MCP_CONNECT")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "MCP_CONNECT machine credential required");
        }
        return principal;
    }
    public UUID requireScope(String scope) {
        RagApiPrincipal p = principal();
        if (!p.scopes().contains(scope)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing scope " + scope);
        return p.apiKeyId();
    }
}
