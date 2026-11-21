package com.ricozknow.enterprise.group;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.EntityGraph; import java.util.*;
public interface GroupRepository extends JpaRepository<Group,UUID>{
 @EntityGraph(attributePaths="members") List<Group> findByTenantIdOrderByNameAsc(UUID tenantId);
 Optional<Group> findByTenantIdAndId(UUID tenantId,UUID id);
 Optional<Group> findByTenantIdAndNameIgnoreCase(UUID tenantId,String name);
}
