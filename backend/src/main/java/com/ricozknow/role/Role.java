package com.ricozknow.role;
import com.ricozknow.common.TenantOwnedEntity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="roles") @Getter @Setter @NoArgsConstructor
public class Role extends TenantOwnedEntity {
    @Column(nullable=false) private String name;
    @Column(name="custom_name") private String customName;
    @Column(nullable=false) private boolean custom=false;
    @Column(nullable=false, columnDefinition="text") private String permissions="";
    public Role(RoleName name){this.name=name.name();}
}
