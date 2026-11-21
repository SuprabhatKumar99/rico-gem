package com.ricozknow.enterprise.experiment;
import com.ricozknow.common.TenantOwnedEntity; import jakarta.persistence.*; import org.hibernate.annotations.JdbcTypeCode; import org.hibernate.type.SqlTypes; import lombok.*;
@Entity @Table(name="ab_experiments") @Getter @Setter @NoArgsConstructor public class AbExperiment extends TenantOwnedEntity {
 @Column(nullable=false) private String key; @Column(nullable=false) private String name; @Column(nullable=false) private String target; @Column(nullable=false) private boolean enabled; @Column(name="traffic_percent",nullable=false) private int trafficPercent=100; @JdbcTypeCode(SqlTypes.JSON) @Column(name="variants_json",columnDefinition="jsonb",nullable=false) private String variantsJson="[]";
}
