package com.ricozknow.enterprise.settings;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSettingsService {

    private final TenantSettingsRepository repo;

    @Transactional
    public TenantSettings getOrCreate() {

        var tenantId = TenantContext.get();

        return repo.findById(tenantId)
            .orElseGet(() -> {

                TenantSettings settings = new TenantSettings();

                settings.setTenantId(tenantId);

                settings.setTimezone("UTC");
                settings.setDefaultLanguage("en");

                settings.setMaxUsers(100);
                settings.setMaxStorageBytes(10737418240L);
                settings.setMaxApiRequestsMonthly(100000);
                settings.setMaxRagQueriesMonthly(25000);
                settings.setMaxAiCostCentsMonthly(100000);

                // IMPORTANT:
                // PostgreSQL has a DEFAULT, but Hibernate explicitly
                // inserts null unless we initialize this value.
                settings.setSettingsJson("{}");

                return repo.save(settings);
            });
    }

    @Transactional
    public TenantSettings save(TenantSettings incoming) {

        TenantSettings settings = getOrCreate();

        settings.setTimezone(incoming.getTimezone());
        settings.setDefaultLanguage(incoming.getDefaultLanguage());

        settings.setMaxUsers(incoming.getMaxUsers());
        settings.setMaxStorageBytes(incoming.getMaxStorageBytes());
        settings.setMaxApiRequestsMonthly(
            incoming.getMaxApiRequestsMonthly()
        );
        settings.setMaxRagQueriesMonthly(
            incoming.getMaxRagQueriesMonthly()
        );
        settings.setMaxAiCostCentsMonthly(
            incoming.getMaxAiCostCentsMonthly()
        );

        // Never allow settings_json to become NULL.
        String settingsJson = incoming.getSettingsJson();

        if (settingsJson == null || settingsJson.isBlank()) {
            settingsJson = "{}";
        }

        settings.setSettingsJson(settingsJson);
        settings.setRetentionDays(incoming.getRetentionDays());
        settings.setPiiRedactionEnabled(incoming.isPiiRedactionEnabled());
        settings.setAiProvider(incoming.getAiProvider()); settings.setAiRegion(incoming.getAiRegion());
        settings.setIpAllowlist(incoming.getIpAllowlist());
        settings.setOidcConfig(incoming.getOidcConfig()==null?"{}":incoming.getOidcConfig());
        settings.setSamlConfig(incoming.getSamlConfig()==null?"{}":incoming.getSamlConfig());

        return repo.save(settings);
    }
}