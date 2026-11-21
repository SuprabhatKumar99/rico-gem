package com.ricozknow.enterprise.scim;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.group.Group;
import com.ricozknow.enterprise.group.GroupRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScimGroupServiceSecurityTest {
    @Mock GroupRepository groups;
    @Mock UserRepository users;
    @Mock AuditService audit;
    ScimGroupService service;
    UUID tenant, id;

    @BeforeEach void setup() {
        tenant = UUID.randomUUID();
        id = UUID.randomUUID();
        TenantContext.set(tenant);
        service = new ScimGroupService(groups, users, audit);
    }
    @AfterEach void clear() { TenantContext.clear(); }

    @Test void cannotReadOrPatchForeignGroup() {
        when(groups.findByTenantIdAndId(tenant, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.patch(id, List.of(new ScimGroupService.Change("add", "members", List.of()))))
                .isInstanceOf(ResponseStatusException.class);
        verify(groups, never()).save(any());
    }

    @Test void cannotAddForeignTenantMember() {
        Group g = new Group(); g.setId(id); g.setTenantId(tenant); g.setName("Engineering");
        UUID foreignUserId = UUID.randomUUID();
        User foreign = new User(); foreign.setId(foreignUserId); foreign.setTenantId(UUID.randomUUID());
        when(groups.findByTenantIdAndId(tenant, id)).thenReturn(Optional.of(g));
        when(users.findById(foreignUserId)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.patch(id, List.of(new ScimGroupService.Change("add", "members",
                List.of(java.util.Map.of("value", foreignUserId.toString()))))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(g.getMembers()).isEmpty();
    }
}
