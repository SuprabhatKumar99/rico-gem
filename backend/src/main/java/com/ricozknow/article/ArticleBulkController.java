package com.ricozknow.article;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
@RestController @RequestMapping("/api/v1/articles/bulk") @RequiredArgsConstructor
public class ArticleBulkController {
 private final ArticleBulkService service;
 @PostMapping(value="/import",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public Map<String,Object> importFiles(@RequestPart("files") List<MultipartFile> files) throws Exception {
  var result=service.importFiles(files); return Map.of("imported",result.size(),"articles",result.stream().map(Article::getId).toList());
 }
 @GetMapping("/export")
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
 public ResponseEntity<byte[]> export(@RequestParam(defaultValue="csv") String format) throws Exception {
  String normalized=format.equalsIgnoreCase("md")?"markdown":format.toLowerCase(java.util.Locale.ROOT);
  boolean csv="csv".equals(normalized);
  byte[] body=csv?service.exportCsv():service.exportArchive(normalized);
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(csv?"text/csv":"application/zip"))
    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ricozknow-articles." + (csv ? "csv" : "zip") + "\"").body(body);
 }
}
