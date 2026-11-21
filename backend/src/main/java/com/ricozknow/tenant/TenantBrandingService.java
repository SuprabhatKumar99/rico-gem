package com.ricozknow.tenant;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantBrandingService {

    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    @Transactional
    public Tenant update(String portalTitle, String logoUrl, String primaryColor, String customDomain, String supportEmail, String supportedLanguages) {
        Tenant tenant = tenantRepository.findById(TenantContext.get())
                .orElseThrow(() -> new IllegalStateException("Tenant not found"));

        tenant.setPortalTitle(portalTitle);
        tenant.setLogoUrl(logoUrl);
        tenant.setPrimaryColor(primaryColor);
        String domain=customDomain == null || customDomain.isBlank()?null:customDomain.trim().toLowerCase(java.util.Locale.ROOT);
        if(domain!=null && (!domain.matches("[a-z0-9](?:[a-z0-9.-]{1,250}[a-z0-9])") || !domain.contains(".") || domain.endsWith(".localhost")))
            throw new IllegalArgumentException("Invalid custom domain");
        if(!java.util.Objects.equals(tenant.getCustomDomain(),domain)){
            tenant.setCustomDomain(domain);
            tenant.setCustomDomainVerified(false);
            tenant.setCustomDomainSslStatus(domain==null?"NOT_CONFIGURED":"PENDING_DNS");
            tenant.setCustomDomainChallengeHash(null);
            tenant.setCustomDomainChallengeCreatedAt(null);
        }
        tenant.setSupportEmail(supportEmail);
        tenant.setSupportedLanguages(supportedLanguages == null || supportedLanguages.isBlank() ? "en" : supportedLanguages);

        auditService.record("Tenant", tenant.getId(), "BRANDING_UPDATED", null, portalTitle);
        return tenant;
    }
}
