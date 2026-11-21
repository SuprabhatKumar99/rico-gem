package com.ricozknow.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false, unique = true)
    private String subdomain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "portal_title")
    private String portalTitle;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name="custom_domain") private String customDomain;
    @Column(name="custom_domain_verified", nullable=false) private boolean customDomainVerified = false;
    @Column(name="custom_domain_ssl_status", nullable=false) private String customDomainSslStatus = "NOT_CONFIGURED";
    @Column(name="custom_domain_challenge_hash",length=64) private String customDomainChallengeHash;
    @Column(name="custom_domain_challenge_created_at") private java.time.Instant customDomainChallengeCreatedAt;
    @Column(name="support_email") private String supportEmail;
    @Column(name="supported_languages", nullable=false, length=1000) private String supportedLanguages = "en";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum TenantStatus {
        ACTIVE, SUSPENDED, ARCHIVED
    }
}
