package com.ricozknow.security;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.mfa.MfaService;
import com.ricozknow.enterprise.sso.SsoConnectionRepository;
import com.ricozknow.enterprise.sso.SsoService;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Explicit, tenant-restricted, paged encryption migration. Never exports secrets. */
@RestController
@RequestMapping("/api/v1/admin/security/secret-rotation")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TenantSecretRotationController {
    private final UserRepository users;
    private final SsoConnectionRepository connections;
    private final SecretEncryptionService secrets;
    private final AuditService audit;

    public record Result(long totalUsers, int page, int size, int reEncryptedUsers, int reEncryptedConnections) {}

    @PostMapping
    @Transactional
    public Result rotate(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "100") int size) {
        if (page < 0 || size < 1 || size > 200)
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Use page >= 0 and size 1..200");
        var tenant = TenantContext.get();
        var usersPage = users.findByTenantId(tenant, PageRequest.of(page, size, org.springframework.data.domain.Sort.by("id").ascending()));
        int updatedUsers = 0;
        for (var user : usersPage.getContent()) {
            if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) continue;
            String updated = secrets.rotate(user.getMfaSecret(), MfaService.context(user));
            if (!updated.equals(user.getMfaSecret())) {
                user.setMfaSecret(updated);
                updatedUsers++;
            }
        }
        int updatedConnections = 0;
        // SSO configs are normally few per tenant; rotate only on first page.
        if (page == 0) for (var connection : connections.findByTenantId(tenant)) {
            String current = connection.getClientSecretEncrypted();
            if (current == null || current.isBlank()) continue;
            String updated = secrets.rotate(current, SsoService.secretContext(connection));
            if (!updated.equals(current)) {
                connection.setClientSecretEncrypted(updated);
                updatedConnections++;
            }
        }
        audit.record("Security", tenant, "TENANT_SECRETS_ROTATED", null,
                "mfa=" + updatedUsers + ";sso=" + updatedConnections + ";page=" + page);
        return new Result(usersPage.getTotalElements(), page, size, updatedUsers, updatedConnections);
    }
}
