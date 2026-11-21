package com.ricozknow.audit;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditWriterService auditWriterService;

    /**
     * Record audit event using the current tenant.
     */
    public void record(
            String entityType,
            UUID entityId,
            String action,
            Object oldState,
            Object newState
    ) {

        UUID tenantId;

        try {
            tenantId = resolveTenantId();
        } catch (Exception ex) {

            log.warn(
                    "Skipping audit event because tenant could not be resolved: " +
                    "{}#{} action={}",
                    entityType,
                    entityId,
                    action,
                    ex
            );

            return;
        }

        recordAfterCommit(
                tenantId,
                entityType,
                entityId,
                action,
                oldState,
                newState
        );
    }

    /**
     * Record audit event for an explicitly supplied tenant.
     *
     * Used by registration, invitation, password reset,
     * verification and other system flows.
     */
    public void recordForTenant(
            UUID tenantId,
            String entityType,
            UUID entityId,
            String action,
            Object oldState,
            Object newState
    ) {

        if (tenantId == null) {

            log.warn(
                    "Skipping audit event because tenantId is null: " +
                    "{}#{} action={}",
                    entityType,
                    entityId,
                    action
            );

            return;
        }

        recordAfterCommit(
                tenantId,
                entityType,
                entityId,
                action,
                oldState,
                newState
        );
    }

    /**
     * If the business transaction is active, wait until it commits.
     *
     * This is important for newly created tenants/users because
     * the tenant row may not yet be visible to a separate
     * REQUIRES_NEW transaction.
     */
    private void recordAfterCommit(
            UUID tenantId,
            String entityType,
            UUID entityId,
            String action,
            Object oldState,
            Object newState
    ) {

        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {

                            auditWriterService.write(
                                    tenantId,
                                    entityType,
                                    entityId,
                                    action,
                                    oldState,
                                    newState
                            );
                        }
                    }
            );

            return;
        }

        /*
         * No surrounding transaction.
         * The writer service starts its own REQUIRES_NEW transaction.
         */
        auditWriterService.write(
                tenantId,
                entityType,
                entityId,
                action,
                oldState,
                newState
        );
    }

    /**
     * Resolve tenant from TenantContext first, then authenticated user.
     */
    private UUID resolveTenantId() {

        UUID tenantId = TenantContext.getOrNull();

        if (tenantId != null) {
            return tenantId;
        }

        var authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication != null
                && authentication.getPrincipal()
                instanceof AuthenticatedUser principal) {

            return principal.tenantId();
        }

        throw new IllegalStateException(
                "Cannot audit-log without a resolvable tenant"
        );
    }
}