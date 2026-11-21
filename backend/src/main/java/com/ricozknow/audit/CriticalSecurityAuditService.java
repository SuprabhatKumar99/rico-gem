package com.ricozknow.audit;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Unlike best-effort operational auditing, critical identity changes are
 * committed in the same database transaction as their audit record. If the
 * audit insert fails, the whole security operation must roll back.
 */
@Service
@RequiredArgsConstructor
public class CriticalSecurityAuditService {
    private final AuditLogRepository logs;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID actorId, String entity, UUID entityId, String action) {
        AuditLog log = new AuditLog();
        log.setTenantId(TenantContext.get());
        log.setActorUserId(actorId);
        log.setEntityType(entity);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setSource("critical-security");
        // Force DB constraints and trigger execution before sensitive operation commits.
        logs.saveAndFlush(log);
    }
}
