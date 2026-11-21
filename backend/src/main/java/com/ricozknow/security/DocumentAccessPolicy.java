package com.ricozknow.security;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.article.Visibility;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.group.GroupService;
import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Shared fail-closed, tenant-local document visibility policy. */
@Component
@RequiredArgsConstructor
public class DocumentAccessPolicy {
    private final GroupService groups;

    /** The portal NEVER reveals PRIVATE or GROUP documents. */
    public boolean canReadPortal(Article article, boolean authenticatedCustomer) {
        if (article == null || !TenantContext.get().equals(article.getTenantId())
                || article.getStatus() != ArticleStatus.PUBLISHED || article.getActiveVersion() == null)
            return false;
        return article.getVisibility() == Visibility.PUBLIC
                || (authenticatedCustomer && article.getVisibility() == Visibility.AUTHENTICATED);
    }

    /** Staff permissions are evaluated against current role grants, tenant and article object. */
    public boolean canReadStaff(Article article, AuthenticatedUser principal) {
        if (article == null || principal == null || !principal.enabled()
                || !TenantContext.get().equals(article.getTenantId())
                || !principal.tenantId().equals(article.getTenantId())) return false;
        Set<String> roles = principal.roleNames();
        if (roles.contains("ADMIN") || roles.contains("EDITOR") || roles.contains("REVIEWER")) return true;
        if (!roles.contains("AGENT_VIEWER") || article.getStatus() != ArticleStatus.PUBLISHED) return false;
        return switch (article.getVisibility()) {
            case PUBLIC, AUTHENTICATED -> true;
            case PRIVATE -> principal.userId().equals(article.getCreatedBy());
            case GROUP -> article.getVisibilityGroup() != null &&
                    article.getVisibilityGroup().getTenantId().equals(principal.tenantId()) &&
                    groups.isMember(principal.userId(), article.getVisibilityGroup().getId());
        };
    }
}
