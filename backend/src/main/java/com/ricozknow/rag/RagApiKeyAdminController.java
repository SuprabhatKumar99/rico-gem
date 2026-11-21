package com.ricozknow.rag;

import com.ricozknow.rag.dto.CreatedRagApiKeyResponse;
import com.ricozknow.rag.dto.CreateRagApiKeyRequest;
import com.ricozknow.rag.dto.RagApiKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/rag-api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RagApiKeyAdminController {
    private final RagApiKeyService service;

    @PostMapping
    public CreatedRagApiKeyResponse create(@Valid @RequestBody CreateRagApiKeyRequest request) {
        var created = service.create(request.name(), request.expiresAt(), request.scopes());
        return new CreatedRagApiKeyResponse(created.id(), created.name(), created.key(), created.prefix(), created.expiresAt());
    }

    @GetMapping
    public List<RagApiKeyResponse> list() {
        return service.list().stream().map(RagApiKeyResponse::from).toList();
    }

    @PostMapping("/{id}/rotate")
    public CreatedRagApiKeyResponse rotate(@PathVariable UUID id) { var c=service.rotate(id); return new CreatedRagApiKeyResponse(c.id(),c.name(),c.key(),c.prefix(),c.expiresAt()); }

    @PostMapping("/{id}/ip-allowlist")
    public void ipAllowlist(@PathVariable UUID id,@RequestBody java.util.Map<String,String> body) { service.setIpAllowlist(id, body.get("allowlist")); }

    @PostMapping("/{id}/revoke")
    public void revoke(@PathVariable UUID id) {
        service.revoke(id);
    }
}
