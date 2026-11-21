package com.ricozknow.security;

import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.group.GroupService;
import com.ricozknow.user.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentAccessPolicyTest {
    private final GroupService groups = mock(GroupService.class);
    private final DocumentAccessPolicy policy = new DocumentAccessPolicy(groups);
    private final UUID tenant = UUID.randomUUID();
    @AfterEach void cleanup() { TenantContext.clear(); }
    private Article article(UUID ownerTenant, Visibility visibility, ArticleStatus status) {
        Article a = new Article();a.setId(UUID.randomUUID());a.setTenantId(ownerTenant);
        a.setVisibility(visibility);a.setStatus(status);
        ArticleVersion version = new ArticleVersion();version.setId(UUID.randomUUID());
        a.setActiveVersion(version);return a;
    }
    @Test void customerCanNeverSeePrivateGroupOrDraftArticles() {
        TenantContext.set(tenant);
        assertThat(policy.canReadPortal(article(tenant, Visibility.PRIVATE, ArticleStatus.PUBLISHED), true)).isFalse();
        assertThat(policy.canReadPortal(article(tenant, Visibility.GROUP, ArticleStatus.PUBLISHED), true)).isFalse();
        assertThat(policy.canReadPortal(article(tenant, Visibility.PUBLIC, ArticleStatus.DRAFT), true)).isFalse();
    }
    @Test void foreignTenantPublicDocumentIsStillDenied() {
        TenantContext.set(tenant);
        assertThat(policy.canReadPortal(article(UUID.randomUUID(), Visibility.PUBLIC, ArticleStatus.PUBLISHED), false)).isFalse();
    }
    @Test void anonymousCustomersOnlySeePublic() {
        TenantContext.set(tenant);
        var a = article(tenant, Visibility.AUTHENTICATED, ArticleStatus.PUBLISHED);
        assertThat(policy.canReadPortal(a, false)).isFalse();
        assertThat(policy.canReadPortal(a, true)).isTrue();
    }
    @Test void agentCannotUseAnotherTenantsAuthorities() {
        TenantContext.set(tenant);
        var a = article(tenant, Visibility.PUBLIC, ArticleStatus.PUBLISHED);
        var foreign = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "agent@test.com", "", Set.of("AGENT_VIEWER"), true);
        assertThat(policy.canReadStaff(a, foreign)).isFalse();
    }
}
