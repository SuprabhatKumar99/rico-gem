// package com.ricozknow.enterprise.settings;
// import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;

// import org.hibernate.annotations.JdbcTypeCode;
// import org.hibernate.type.SqlTypes;
// @Entity @Table(name="tenant_settings") @Getter @Setter @NoArgsConstructor public class TenantSettings {
//  @Id @Column(name="tenant_id") private UUID tenantId;
//  @Column(nullable=false) private String timezone="UTC";
//  @Column(name="default_language",nullable=false) private String defaultLanguage="en";
//  @Column(nullable=false) private int maxUsers=100;
//  @Column(nullable=false) private long maxStorageBytes=10737418240L;
//  @Column(nullable=false) private long maxApiRequestsMonthly=100000;
//  @Column(nullable=false) private long maxRagQueriesMonthly=25000;
//  @Column(nullable=false) private long maxAiCostCentsMonthly=100000;

// //  @Column(name="settings_json",columnDefinition="text") private String settingsJson="{}";
//     @JdbcTypeCode(SqlTypes.JSON)
//     @Column(name = "settings_json", columnDefinition = "jsonb")
//     private String settingsJson ;

//  @Column(nullable=false) private Instant createdAt=Instant.now(); @Column(nullable=false) private Instant updatedAt=Instant.now();
// }



package com.ricozknow.enterprise.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings")
@Getter
@Setter
@NoArgsConstructor
public class TenantSettings {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String timezone = "UTC";

    @Column(name = "default_language", nullable = false)
    private String defaultLanguage = "en";

    @Column(nullable = false)
    private int maxUsers = 100;

    @Column(nullable = false)
    private long maxStorageBytes = 10737418240L;

    @Column(nullable = false)
    private long maxApiRequestsMonthly = 100000;

    @Column(nullable = false)
    private long maxRagQueriesMonthly = 25000;

    @Column(nullable = false)
    private long maxAiCostCentsMonthly = 100000;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
        name = "settings_json",
        columnDefinition = "jsonb",
        nullable = false
    )
    private String settingsJson = "{}";


    @Column(name="retention_days", nullable=false) private int retentionDays = 3650;
    @Column(name="pii_redaction_enabled", nullable=false) private boolean piiRedactionEnabled = true;
    @Column(name="ai_provider") private String aiProvider;
    @Column(name="ai_region") private String aiRegion;
    @Column(name="ip_allowlist", columnDefinition="text") private String ipAllowlist;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="oidc_config", columnDefinition="jsonb", nullable=false) private String oidcConfig = "{}";
    @JdbcTypeCode(SqlTypes.JSON) @Column(name="saml_config", columnDefinition="jsonb", nullable=false) private String samlConfig = "{}";

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}