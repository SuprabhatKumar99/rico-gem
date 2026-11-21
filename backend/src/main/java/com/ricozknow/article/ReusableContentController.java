package com.ricozknow.article;
import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/articles/library") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
public class ReusableContentController {
 private final ReusableContentService service;
 public record TemplateView(UUID id,String key,String name,String description,JsonNode content){}
 public record BlockView(UUID id,String name,JsonNode block){}
 @GetMapping("/templates") public List<TemplateView> templates(){return service.templates().stream().map(t->new TemplateView(t.getId(),t.getTemplateKey(),t.getName(),t.getDescription(),t.getContent())).toList();}
 @PostMapping("/templates") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public TemplateView createTemplate(@Valid @RequestBody ContentTemplateRequest request){var t=service.createTemplate(request);return new TemplateView(t.getId(),t.getTemplateKey(),t.getName(),t.getDescription(),t.getContent());}
 @DeleteMapping("/templates/{id}") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id){service.deleteTemplate(id);return ResponseEntity.noContent().build();}
 @GetMapping("/blocks") public List<BlockView> blocks(){return service.blocks().stream().map(b->new BlockView(b.getId(),b.getName(),b.getBlock())).toList();}
 @PostMapping("/blocks") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public BlockView createBlock(@Valid @RequestBody ReusableBlockRequest request){var b=service.createBlock(request);return new BlockView(b.getId(),b.getName(),b.getBlock());}
 @DeleteMapping("/blocks/{id}") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public ResponseEntity<Void> deleteBlock(@PathVariable UUID id){service.deleteBlock(id);return ResponseEntity.noContent().build();}
}
