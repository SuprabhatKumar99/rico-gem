package com.ricozknow.enterprise.scim;
import com.ricozknow.common.TenantOwnedEntity; import com.fasterxml.jackson.annotation.JsonIgnore; import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="scim_tokens") @Getter @Setter @NoArgsConstructor public class ScimToken extends TenantOwnedEntity {private String name; @JsonIgnore @Column(name="token_hash",nullable=false,unique=true) private String tokenHash; @Column(name="token_prefix",nullable=false) private String tokenPrefix; private boolean active=true; private Instant lastUsedAt;}
