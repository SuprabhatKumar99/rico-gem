package com.ricozknow.customer;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="customer_users", uniqueConstraints=@UniqueConstraint(columnNames={"tenant_id","email"}))
@Getter @Setter @NoArgsConstructor
public class CustomerUser extends TenantOwnedEntity {
 @Id @GeneratedValue private UUID id;
 @Column(nullable=false) private String email;
 private String name;
 @Column(nullable=false) private String status="ACTIVE";
 private Instant lastLoginAt;
}