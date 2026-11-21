package com.ricozknow.enterprise.group;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/groups") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class GroupController {
 private final GroupService s;
 record Create(String name,String externalId){} record Member(UUID userId){}
 @GetMapping public List<Group> list(){return s.list();}
 @PostMapping public Group create(@RequestBody Create r){return s.create(r.name(),r.externalId());}
 @PostMapping("/{id}/members") public Group add(@PathVariable UUID id,@RequestBody Member r){return s.addMember(id,r.userId());}
 @DeleteMapping("/{id}/members/{userId}") public Group remove(@PathVariable UUID id,@PathVariable UUID userId){return s.removeMember(id,userId);}
}
