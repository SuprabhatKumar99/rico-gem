package com.ricozknow.enterprise.group;
import com.ricozknow.common.TenantOwnedEntity; import com.ricozknow.user.User; import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="groups") @Getter @Setter @NoArgsConstructor
public class Group extends TenantOwnedEntity {
 @Column(nullable=false) private String name; @Column(name="external_id") private String externalId;
 @ManyToMany(fetch=FetchType.LAZY) @JoinTable(name="group_memberships",joinColumns=@JoinColumn(name="group_id"),inverseJoinColumns=@JoinColumn(name="user_id"))
 private Set<User> members=new HashSet<>();
}
