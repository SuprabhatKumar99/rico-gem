package com.ricozknow.search;

import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.CustomerAuthService;
import com.ricozknow.customer.CustomerUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SavedSearchAccessTest {
    SavedSearchRepository repo = mock(SavedSearchRepository.class);
    CustomerAuthService customerAuth = mock(CustomerAuthService.class);
    SavedSearchController service = new SavedSearchController(repo, customerAuth);
    UUID tenant = UUID.randomUUID();
    @AfterEach void cleanup() { TenantContext.clear(); }
    CustomerUser customer(UUID t) {
        CustomerUser user = new CustomerUser();user.setTenantId(t);user.setEmail("mine@example.com");
        return user;
    }

    @Test void anonymousCannotListOrDeleteSubscriberData() {
        TenantContext.set(tenant);
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(null, UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }

    @Test void tenantIdInQueryIsInsufficientToAccessAnotherTenantsAccount() {
        TenantContext.set(tenant);
        when(customerAuth.current("cookie")).thenReturn(customer(UUID.randomUUID()));
        assertThatThrownBy(() -> service.list("cookie")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repo);
    }

    @Test void authenticatedCustomerCannotSubscribeAnotherEmail() {
        TenantContext.set(tenant);
        when(customerAuth.current("cookie")).thenReturn(customer(tenant));
        SavedSearchController.Request body = new SavedSearchController.Request(
                "other@example.com", "test", null,null,null,null,null,null);
        assertThatThrownBy(() -> service.create("cookie",body)).isInstanceOf(ResponseStatusException.class);
        verify(repo,never()).save(any());
    }

    @Test void authenticatedCustomerReadsOnlyOwnSubscriptions() {
        TenantContext.set(tenant);
        when(customerAuth.current("cookie")).thenReturn(customer(tenant));
        when(repo.findByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(tenant,"mine@example.com"))
                .thenReturn(List.of());
        assertThat(service.list("cookie")).isEmpty();
        verify(repo).findByTenantIdAndEmailIgnoreCaseOrderByCreatedAtDesc(tenant,"mine@example.com");
        verify(repo,never()).findByTenantIdOrderByCreatedAtDesc(any());
    }
}
