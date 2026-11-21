package com.ricozknow.ingestion;

import com.ricozknow.ingestion.dto.KnowledgeSourceResponse;
import com.ricozknow.common.TenantContext;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/v1/knowledge-sources") @RequiredArgsConstructor
public class KnowledgeSourceController {
    private final KnowledgeIngestionService service;
    private final KnowledgeSourceRepository repository;

    @PostMapping(value="/documents", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse upload(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue="en") String language, @RequestParam(required=false) Integer syncIntervalMinutes){ return KnowledgeSourceResponse.from(service.upload(file, language, syncIntervalMinutes)); }

    @PostMapping(value="/documents/bulk", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<KnowledgeSourceResponse> bulkUpload(@RequestPart("files") List<MultipartFile> files, @RequestParam(defaultValue="en") String language, @RequestParam(required=false) Integer syncIntervalMinutes){ return service.bulkUpload(files, language, syncIntervalMinutes).stream().map(KnowledgeSourceResponse::from).toList(); }

    @PostMapping("/url") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse url(@RequestParam @NotBlank String url, @RequestParam(required=false) String name, @RequestParam(defaultValue="en") String language, @RequestParam(required=false) Integer syncIntervalMinutes, @RequestParam(defaultValue="25") Integer maxPages){ return KnowledgeSourceResponse.from(service.createUrl(url,name,language,syncIntervalMinutes,maxPages,false)); }

    @PostMapping("/website") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse website(@RequestParam @NotBlank String url, @RequestParam(required=false) String name, @RequestParam(defaultValue="en") String language, @RequestParam(required=false) Integer syncIntervalMinutes, @RequestParam(defaultValue="25") Integer maxPages){ return KnowledgeSourceResponse.from(service.createUrl(url,name,language,syncIntervalMinutes,maxPages,true)); }

    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<KnowledgeSourceResponse> list(){ return repository.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()).stream().map(KnowledgeSourceResponse::from).toList(); }

    @PostMapping("/{id}/sync") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse sync(@PathVariable UUID id){ return KnowledgeSourceResponse.from(service.sync(id)); }

    @PostMapping("/{id}/reindex") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse reindex(@PathVariable UUID id){ return KnowledgeSourceResponse.from(service.reindex(id)); }

    @PostMapping("/{id}/retry") @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public KnowledgeSourceResponse retry(@PathVariable UUID id){ return KnowledgeSourceResponse.from(service.retry(id)); }

    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void delete(@PathVariable UUID id){ service.delete(id); }
}
