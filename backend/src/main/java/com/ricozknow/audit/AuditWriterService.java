package com.ricozknow.audit;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ricozknow.user.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditWriterService {

    private final AuditLogRepository auditLogRepository;
    private final AuditStateSanitizer stateSanitizer;

    /**
     * Writes an audit record in its own transaction.
     *
     * IMPORTANT:
     * This method lives in a separate Spring bean so that
     * @Transactional(REQUIRES_NEW) is actually applied through
     * Spring's proxy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(
            UUID tenantId,
            String entityType,
            UUID entityId,
            String action,
            Object oldState,
            Object newState
    ) {
        try {
            if (tenantId == null) {
                log.warn(
                        "Skipping audit log because tenantId is null: {}#{} action={}",
                        entityType,
                        entityId,
                        action
                );
                return;
            }

            AuditLog auditLog = new AuditLog();

            auditLog.setTenantId(tenantId);
            auditLog.setActorUserId(resolveActorUserId());

            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setAction(action);

            auditLog.setOldState(stateSanitizer.safeJson(oldState));
            auditLog.setNewState(stateSanitizer.safeJson(newState));

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes)
                            RequestContextHolder.getRequestAttributes();

            if (attributes != null) {

                var request = attributes.getRequest();

                auditLog.setIpAddress(
                        request.getRemoteAddr()
                );

                auditLog.setUserAgent(
                        request.getHeader("User-Agent")
                );

                auditLog.setRequestId(
                        request.getHeader("X-Request-Id")
                );

                String source =
                        request.getHeader("X-Audit-Source");

                auditLog.setSource(
                        source != null && !source.isBlank()
                                ? source
                                : "api"
                );

            } else {
                auditLog.setSource("system");
            }

            auditLogRepository.save(auditLog);

            log.debug(
                    "Audit log written: tenantId={} entity={}#{} action={}",
                    tenantId,
                    entityType,
                    entityId,
                    action
            );

        } catch (Exception ex) {

            /*
             * Audit logging must never break the business transaction.
             */
            log.error(
                    "Failed to write audit log for {}#{} action={}",
                    entityType,
                    entityId,
                    action,
                    ex
            );
        }
    }

    private UUID resolveActorUserId() {

        var authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication != null
                && authentication.getPrincipal()
                instanceof AuthenticatedUser principal) {

            return principal.userId();
        }

        return null;
    }
}