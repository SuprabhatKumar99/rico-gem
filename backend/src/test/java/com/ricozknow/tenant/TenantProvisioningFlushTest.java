package com.ricozknow.tenant;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningFlushTest {
    @Mock TenantRepository tenants;
    @Mock RoleRepository roles;
    @Mock AuditService audit;
    @Mock JdbcTemplate db;

    @Test void tenantInsertIsFlushedBeforeSubscriptionForeignKeyInsert() {
        UUID id = UUID.randomUUID();
        when(tenants.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(id);
            return tenant;
        });
        TenantProvisioningService service = new TenantProvisioningService(tenants, roles, audit, db);
        assertThat(service.provision("Acme", "acme", "acme").getId()).isEqualTo(id);
        InOrder order = inOrder(tenants, db);
        order.verify(tenants).saveAndFlush(any(Tenant.class));
        order.verify(db).update(startsWith("insert into saas_subscription"), eq(id));
        assertThat(TenantContext.getOrNull()).isNull();
    }
}
