package com.ricozknow.enterprise.group;
import com.ricozknow.audit.AuditService; import com.ricozknow.common.TenantContext; import com.ricozknow.user.UserRepository; import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.*;
@Service @RequiredArgsConstructor
public class GroupService {
 private final GroupRepository groups; private final UserRepository users; private final AuditService audit;
 @Transactional(readOnly=true) public List<Group> list(){return groups.findByTenantIdOrderByNameAsc(TenantContext.get());}
 @Transactional public Group create(String name,String externalId){Group g=new Group();g.setTenantId(TenantContext.get());g.setName(name.trim());g.setExternalId(externalId);groups.save(g);audit.record("Group",g.getId(),"GROUP_CREATED",null,name);return g;}
 @Transactional public Group addMember(UUID gid,UUID uid){Group g=get(gid);var u=users.findById(uid).filter(x->x.getTenantId().equals(TenantContext.get())).orElseThrow();g.getMembers().add(u);return g;}
 @Transactional public Group removeMember(UUID gid,UUID uid){Group g=get(gid);g.getMembers().removeIf(u->u.getId().equals(uid));return g;}
 private Group get(UUID id){return groups.findByTenantIdAndId(TenantContext.get(),id).orElseThrow(()->new IllegalArgumentException("Group not found"));}
 @Transactional(readOnly=true) public boolean isMember(UUID userId,UUID groupId){return groupId!=null && groups.findByTenantIdAndId(TenantContext.get(),groupId).map(g->g.getMembers().stream().anyMatch(u->u.getId().equals(userId))).orElse(false);}
}
