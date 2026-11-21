package com.ricozknow.enterprise.sso;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/sso") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class SsoController {private final SsoService s; @GetMapping public List<SsoConnection> list(){return s.list();} @PutMapping public SsoConnection save(@RequestBody SsoConnection c){return s.save(c);}
}
