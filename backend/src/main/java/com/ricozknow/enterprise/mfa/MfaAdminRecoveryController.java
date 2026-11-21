package com.ricozknow.enterprise.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/mfa-recovery")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MfaAdminRecoveryController {
    private final MfaAdminRecoveryService service;

    public record Request(@NotNull UUID targetUserId, @NotBlank String externalTicket, @NotBlank String reason) {}
    public record View(UUID id, UUID targetUserId, UUID requestedBy, UUID approvedBy,
                       String externalTicket, String reason, Instant expiresAt, Instant approvedAt,
                       MfaAdminRecoveryRequest.Status status) {
        static View from(MfaAdminRecoveryRequest r) {
            return new View(r.getId(), r.getTargetUserId(), r.getRequestedBy(), r.getApprovedBy(),
                    r.getExternalTicket(), r.getReason(), r.getExpiresAt(), r.getApprovedAt(), r.getStatus());
        }
    }
    @PostMapping
    public View request(@Valid @RequestBody Request body) {
        return View.from(service.request(body.targetUserId(), body.externalTicket(), body.reason()));
    }
    @PostMapping("/{id}/approve")
    public View approve(@PathVariable UUID id) { return View.from(service.approve(id)); }
    @GetMapping
    public Page<View> list(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size).map(View::from);
    }
}
