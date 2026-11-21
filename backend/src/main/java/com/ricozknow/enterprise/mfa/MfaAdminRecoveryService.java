package com.ricozknow.enterprise.mfa;

import com.ricozknow.audit.AuditService;
import com.ricozknow.audit.CriticalSecurityAuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Break-glass recovery with distinct, MFA-enabled administrators and an external
 * identity-verification ticket. Ticket existence is not proof of identity:
 * operators MUST verify identity out of band before creating the request.
 */
@Service
@RequiredArgsConstructor
public class MfaAdminRecoveryService {
    private final MfaAdminRecoveryRepository requests;
    private final UserRepository users;
    private final MfaService mfa;
    private final AuditService audit;
    private final CriticalSecurityAuditService criticalAudit;

    @Transactional
    public MfaAdminRecoveryRequest request(UUID targetId, String ticket, String reason) {
        UUID tenant = TenantContext.get();
        User admin = requireMfaAdmin(currentUserId());
        User target = requireTenantUser(targetId);
        if (target.getId().equals(admin.getId())) reject("Self-service administrative reset is forbidden");
        if (!target.isMfaEnabled()) reject("Target does not have MFA enabled");
        if (ticket == null || !ticket.matches("[A-Za-z0-9._:/-]{6,160}")
                || reason == null || reason.isBlank() || reason.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "External ticket (6-160 chars) and reason required");
        MfaAdminRecoveryRequest request = new MfaAdminRecoveryRequest();
        request.setTenantId(tenant);
        request.setTargetUserId(targetId);
        request.setRequestedBy(admin.getId());
        request.setExternalTicket(ticket);
        request.setReason(reason.trim());
        request.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        requests.save(request);
        criticalAudit.append(admin.getId(), "MfaAdminRecovery", request.getId(), "MFA_RESET_REQUESTED");
        return request;
    }

    @Transactional
    public MfaAdminRecoveryRequest approve(UUID requestId) {
        User approver = requireMfaAdmin(currentUserId());
        MfaAdminRecoveryRequest request = requests.findByTenantIdAndId(TenantContext.get(), requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery request not found"));
        if (request.getStatus() != MfaAdminRecoveryRequest.Status.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Recovery request is not pending");
        if (!request.getExpiresAt().isAfter(Instant.now())) {
            request.setStatus(MfaAdminRecoveryRequest.Status.EXPIRED);
            return request;
        }
        if (approver.getId().equals(request.getRequestedBy())
                || approver.getId().equals(request.getTargetUserId()))
            reject("A different administrator must approve; target cannot approve own recovery");
        // The requesting administrator must STILL be a tenant-local MFA-enabled admin.
        requireMfaAdmin(request.getRequestedBy());
        User target = requireTenantUser(request.getTargetUserId());
        if (!target.isMfaEnabled())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Target MFA is already disabled");
        mfa.resetAfterDualAdminApproval(target);
        request.setApprovedBy(approver.getId());
        request.setApprovedAt(Instant.now());
        request.setStatus(MfaAdminRecoveryRequest.Status.APPROVED);
        criticalAudit.append(approver.getId(), "MfaAdminRecovery", request.getId(), "MFA_RESET_APPROVED");
        return request;
    }

    @Transactional(readOnly = true)
    public Page<MfaAdminRecoveryRequest> list(int page, int size) {
        requireMfaAdmin(currentUserId());
        if (page < 0 || size < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return requests.findByTenantId(TenantContext.get(), PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    private UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof com.ricozknow.user.AuthenticatedUser p))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Staff authentication required");
        if (!TenantContext.get().equals(p.tenantId())) reject("Tenant mismatch");
        return p.userId();
    }

    private User requireTenantUser(UUID id) {
        return users.findById(id).filter(u -> TenantContext.get().equals(u.getTenantId()))
                .filter(u -> u.getStatus() == User.UserStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private User requireMfaAdmin(UUID id) {
        User admin = requireTenantUser(id);
        if (!admin.isMfaEnabled() || admin.getRoles().stream().noneMatch(r -> "ADMIN".equals(r.getName())))
            reject("Active MFA-enabled tenant administrator required");
        return admin;
    }

    private static void reject(String message) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
