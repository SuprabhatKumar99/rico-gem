package com.ricozknow.enterprise.scim;

import com.ricozknow.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScimFilterControllerTest {
    @Test void userNameLookupUsesTenantScopedServiceAndRejectsUnsupportedFilters() {
        var service = mock(ScimService.class);
        var controller = new ScimController(service);
        User user = new User();
        user.setId(UUID.randomUUID()); user.setEmail("person@example.com"); user.setName("Person");
        when(service.findByUserName("person@example.com")).thenReturn(Optional.of(user));
        var result = controller.list(1, 100, "userName eq \"person@example.com\"");
        assertThat(result.get("totalResults")).isEqualTo(1);
        verify(service).findByUserName("person@example.com");
        assertThatThrownBy(() -> controller.list(1, 100, "userName co \"example\""))
                .isInstanceOf(ResponseStatusException.class);
        verify(service, never()).list(anyInt(), anyInt());
    }

    @Test void groupDisplayNameLookupIsRestrictedToExactFilter() {
        var service = mock(ScimGroupService.class);
        var controller = new ScimGroupController(service);
        when(service.findByDisplayName("Engineering")).thenReturn(Optional.of(
                new ScimGroupService.GroupView(UUID.randomUUID(), "Engineering", null, java.util.List.of())));
        var result = controller.list(1, 50, "displayName eq \"Engineering\"");
        assertThat(result.get("totalResults")).isEqualTo(1);
        verify(service).findByDisplayName("Engineering");
        assertThatThrownBy(() -> controller.list(1, 50, "members pr"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
