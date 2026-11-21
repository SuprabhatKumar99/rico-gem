package com.ricozknow.role;
import com.ricozknow.audit.AuditService; import com.ricozknow.common.TenantContext; import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service @RequiredArgsConstructor
public class CustomRoleService {
 private final RoleRepository repo; private final AuditService audit;
 @Transactional public Role create(String name, Set<String> permissions){
   if(name==null||name.isBlank()) throw new IllegalArgumentException("Role name is required");
   String normalized=name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+","_");
   if(EnumSet.allOf(RoleName.class).stream().anyMatch(x->x.name().equals(normalized))) throw new IllegalArgumentException("Reserved role name");
   Role r=new Role(); r.setTenantId(TenantContext.get()); r.setName(normalized); r.setCustomName(name.trim()); r.setCustom(true);
   r.setPermissions(String.join(",", permissions==null?Set.of():permissions)); repo.save(r);
   audit.record("Role",r.getId(),"CUSTOM_ROLE_CREATED",null,normalized); return r;
 }
 @Transactional(readOnly=true) public List<Role> list(){return repo.findByTenantId(TenantContext.get());}
}
