package com.ricozknow.role;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/roles") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class RoleAdminController {private final CustomRoleService s; record Create(String name,Set<String> permissions){} @GetMapping public List<Role> list(){return s.list();}@PostMapping public Role create(@RequestBody Create r){return s.create(r.name(),r.permissions());}}
