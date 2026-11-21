package com.ricozknow.aiplatform;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/v1/ai-platform/governance")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class AiGovernanceController {
    private final AiUsageService usage;
    @GetMapping("/usage") public Map<String,Object> usage(){return usage.overview();}
}
