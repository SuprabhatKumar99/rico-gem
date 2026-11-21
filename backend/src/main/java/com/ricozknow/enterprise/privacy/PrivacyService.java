package com.ricozknow.enterprise.privacy;
import com.ricozknow.audit.AuditService; import com.ricozknow.auth.RefreshTokenService; import com.ricozknow.common.TenantContext; import com.ricozknow.user.*;
import com.ricozknow.enterprise.group.GroupRepository; import com.fasterxml.jackson.databind.ObjectMapper; import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.Instant; import java.util.*;
@Service @RequiredArgsConstructor
public class PrivacyService {
 private final UserRepository users; private final RefreshTokenService refresh; private final AuditService audit; private final GroupRepository groups; private final ObjectMapper mapper;
 @Transactional(readOnly=true) public Map<String,Object> export(UUID userId){
   User u=users.findById(userId).filter(x->x.getTenantId().equals(TenantContext.get())).orElseThrow();
   Map<String,Object> m=new LinkedHashMap<>(); m.put("id",u.getId());m.put("email",u.getEmail());m.put("name",u.getName());m.put("status",u.getStatus());m.put("roles",u.getRoles().stream().map(r->r.getName()).toList());m.put("exportedAt",Instant.now().toString()); return m;
 }
 @Transactional public void delete(UUID userId){
   User u=users.findById(userId).filter(x->x.getTenantId().equals(TenantContext.get())).orElseThrow();
   refresh.revokeAllForUser(userId); groups.findByTenantIdOrderByNameAsc(TenantContext.get()).forEach(g->g.getMembers().removeIf(x->x.getId().equals(userId)));
   u.setEmail("deleted+"+userId+"@privacy.invalid");u.setName("Deleted User");u.setPasswordHash(UUID.randomUUID().toString());u.setStatus(User.UserStatus.DISABLED);u.setEmailVerified(false);u.setMfaEnabled(false);u.setMfaSecret(null);
   audit.record("User",userId,"GDPR_USER_DELETED",null,null);
 }
}
