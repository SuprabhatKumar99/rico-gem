package com.ricozknow.enterprise.scope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Set; import java.util.UUID;
@RestController @RequestMapping("/api/v1/admin/api-keys") @RequiredArgsConstructor
public class ApiScopeController {
 private final ApiScopeService service;
 @PutMapping("/{id}/scopes") @PreAuthorize("hasRole('ADMIN')") public Set<String> update(@PathVariable UUID id,@RequestBody Set<String> scopes){return service.update(id,scopes);}
 @GetMapping("/{id}/scopes") @PreAuthorize("hasRole('ADMIN')") public Set<String> get(@PathVariable UUID id){return service.get(id);}
}
