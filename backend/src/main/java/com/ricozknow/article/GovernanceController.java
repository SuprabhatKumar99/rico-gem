package com.ricozknow.article;
import com.ricozknow.article.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/articles/governance") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
public class GovernanceController {
 private final ArticleGovernanceService service;
 @GetMapping("/health") public KnowledgeHealthResponse health(){return service.health();}
 @GetMapping("/{articleId}") public GovernanceResponse get(@PathVariable UUID articleId){return service.get(articleId);}
 @PutMapping("/{articleId}") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public GovernanceResponse update(@PathVariable UUID articleId,@Valid @RequestBody GovernanceRequest request){return service.update(articleId,request);}
 @GetMapping("/{articleId}/quality") public ContentQualityResponse quality(@PathVariable UUID articleId){return service.quality(articleId);}
 @PostMapping("/{articleId}/verify") @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
 public GovernanceResponse verify(@PathVariable UUID articleId){return service.verify(articleId);}
 @GetMapping("/{articleId}/history") public List<Map<String,Object>> history(@PathVariable UUID articleId){return service.verificationHistory(articleId);}
}
