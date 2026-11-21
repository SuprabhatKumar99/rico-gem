package com.ricozknow.enterprise.privacy;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/privacy") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class PrivacyController { private final PrivacyService s;
 @GetMapping("/users/{id}/export") public Map<String,Object> export(@PathVariable UUID id){return s.export(id);}
 @DeleteMapping("/users/{id}") public void delete(@PathVariable UUID id){s.delete(id);}
}
