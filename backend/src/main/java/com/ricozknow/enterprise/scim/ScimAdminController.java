package com.ricozknow.enterprise.scim;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/scim") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class ScimAdminController {private final ScimService s; @PostMapping("/tokens") public ScimService.TokenResult create(@RequestBody Map<String,String> b){return s.createToken(b.getOrDefault("name","SCIM"));}@GetMapping("/tokens") public List<ScimToken> list(){return s.listTokens();}@PostMapping("/tokens/{id}/revoke") public void revoke(@PathVariable UUID id){s.revokeToken(id);}}
