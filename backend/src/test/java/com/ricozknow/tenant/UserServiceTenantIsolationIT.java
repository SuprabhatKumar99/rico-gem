package com.ricozknow.tenant;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import com.ricozknow.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sprint 1 regression: authenticated tenant-scoped user operations must reject cross-tenant IDs. */
@Testcontainers
@SpringBootTest
class UserServiceTenantIsolationIT {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ricozknow_test")
            .withUsername("ricozknow")
            .withPassword("ricozknow");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TenantProvisioningService tenantProvisioningService;
    @Autowired UserRepository userRepository;
    @Autowired UserService userService;

    @AfterEach
    void clearTenant() { TenantContext.clear(); }

    @Test
    void adminCannotDisableAnotherTenantsUser() {
        Tenant acme = tenantProvisioningService.provision("Acme", "acme-sec", "acme-sec");
        Tenant globex = tenantProvisioningService.provision("Globex", "globex-sec", "globex-sec");

        TenantContext.set(acme.getId());
        User user = new User();
        user.setEmail("user@acme.test");
        user.setName("Acme User");
        user.setPasswordHash("hash");
        userRepository.saveAndFlush(user);
        TenantContext.clear();

        TenantContext.set(globex.getId());
        assertThatThrownBy(() -> userService.disable(user.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not found");
    }
}
