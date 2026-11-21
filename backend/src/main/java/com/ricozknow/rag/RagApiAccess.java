package com.ricozknow.rag;

import com.ricozknow.common.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Machine RAG credentials are scoped to an API key AND a tenant, not merely a tenant. */
public final class RagApiAccess {
    private RagApiAccess() {}
    public static UUID keyId() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        Object principal=authentication==null?null:authentication.getPrincipal();
        if (!(principal instanceof RagApiPrincipal apiKey)
                || !apiKey.tenantId().equals(TenantContext.get())
                || !apiKey.scopes().contains("RAG_SEARCH"))
            throw new RagApiException("RAG_ACCESS_DENIED", "RAG API key required", HttpStatus.FORBIDDEN, false);
        return apiKey.apiKeyId();
    }
}
