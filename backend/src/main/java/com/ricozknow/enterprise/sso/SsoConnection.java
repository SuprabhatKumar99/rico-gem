package com.ricozknow.enterprise.sso;
import com.ricozknow.common.TenantOwnedEntity; import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import com.fasterxml.jackson.annotation.JsonProperty; import lombok.*;
@Entity @Table(name="sso_connections") @Getter @Setter @NoArgsConstructor
public class SsoConnection extends TenantOwnedEntity {
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Provider provider;
 @Column(nullable=false) private boolean enabled;
 private String clientId; @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) @Column(columnDefinition="text") private String clientSecretEncrypted;
 @Column(columnDefinition="text") private String issuerUrl; @Column(columnDefinition="text") private String authorizationUrl; @Column(columnDefinition="text") private String tokenUrl; @Column(columnDefinition="text") private String userinfoUrl; @Column(columnDefinition="text") private String metadataUrl; @Column(columnDefinition="text") private String entityId;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb",nullable=false) private String settingsJson="{}";
 @Column(columnDefinition="text") private String samlIdpEntityId;
 @Column(columnDefinition="text") private String samlIdpSsoUrl;
 @Column(columnDefinition="text") private String samlIdpCertificate;
 @Column(columnDefinition="text") private String samlSpEntityId;
 @Column(nullable=false) private boolean samlWantAuthnRequestsSigned=false;
 public enum Provider { OIDC,SAML }
}
