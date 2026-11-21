package com.ricozknow.tenant.dto;

import com.ricozknow.tenant.Tenant;

import java.util.UUID;

/**
 * Deliberately excludes anything sensitive — this is fetched by the portal
 * before any authentication happens, purely to render branding and resolve
 * the tenant slug the login form should submit.
 */
public record TenantLookupResponse(
        UUID tenantId,
        String slug,
        String name,
        String portalTitle,
        String logoUrl,
        String primaryColor,
        String customDomain,
        boolean customDomainVerified,
        String customDomainSslStatus,
        String supportEmail,
        String supportedLanguages
) {
    public static TenantLookupResponse from(Tenant tenant) {
        return new TenantLookupResponse(
                tenant.getId(), tenant.getSlug(), tenant.getName(),
                tenant.getPortalTitle(), tenant.getLogoUrl(), tenant.getPrimaryColor(), tenant.getCustomDomain(), tenant.isCustomDomainVerified(), tenant.getCustomDomainSslStatus(), tenant.getSupportEmail(), tenant.getSupportedLanguages());
    }
}
