package com.ricozknow.enterprise.experiment;
import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/admin/experiments") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AbController {private final AbService s; @GetMapping public List<AbExperiment> list(){return s.list();}@PostMapping public AbExperiment save(@RequestBody AbExperiment e){return s.save(e);}@GetMapping("/{key}/assignment") public Map<String,String> assign(@PathVariable String key,@RequestParam String subject){return Map.of("variant",s.assign(key,subject));}}
