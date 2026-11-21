package com.ricozknow.innovation;

import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleRepository;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.common.TenantContext;
import com.ricozknow.ingestion.KnowledgeSourceRepository;
import com.ricozknow.ingestion.dto.KnowledgeSourceResponse;
import com.ricozknow.rag.RagAnswerService;
import com.ricozknow.rag.dto.RagAnswerRequest;
import com.ricozknow.rag.dto.RagAnswerResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Portal workspace: tenant-isolated quality signals, existing RAG and ingestion adapters. */
@RestController
@RequestMapping("/api/v1/innovation")
@RequiredArgsConstructor
public class InnovationController {
 private final ArticleRepository articles;
 private final KnowledgeSourceRepository sources;
 private final RagAnswerService rag;
 public record CopilotPrompt(@NotBlank String question, UUID conversationId) {}
 public record QualityFinding(UUID articleId,String title,String rule,String severity,String recommendation) {}
 public record QualityReport(int totalArticles, int findingsCount,Instant generatedAt,List<QualityFinding> findings) {}
 public record ConnectorOption(String id,String name,String kind,String status,String description) {}

 @PostMapping("/copilot/ask")
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
 public RagAnswerResponse ask(@Valid @RequestBody CopilotPrompt prompt) {
  return rag.answer(new RagAnswerRequest(prompt.question(),prompt.conversationId(),8,null,null,null,null));
 }

 @GetMapping("/quality")
 @Transactional(readOnly=true)
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
 public QualityReport quality() {
  List<Article> all=articles.findAllByTenantId(TenantContext.get());
  List<QualityFinding> findings=new ArrayList<>();
  Map<String,List<Article>> titles=all.stream().filter(a->a.getTitle()!=null && !a.getTitle().isBlank())
   .collect(Collectors.groupingBy(a->a.getTitle().trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT)));
  Instant stale=Instant.now().minus(120,ChronoUnit.DAYS);
  for(Article a:all) {
   if(a.getStatus()==ArticleStatus.PUBLISHED && (a.getRagIndexedAt()==null || a.getRagIndexedAt().isBefore(stale)))
    findings.add(new QualityFinding(a.getId(),a.getTitle(),"STALE_RAG_INDEX","MEDIUM","Reindex this article for AI retrieval"));
   if(a.getStatus()==ArticleStatus.PUBLISHED && (a.getSearchIndexedAt()==null || a.getSearchIndexedAt().isBefore(stale)))
    findings.add(new QualityFinding(a.getId(),a.getTitle(),"STALE_SEARCH_INDEX","MEDIUM","Refresh keyword search indexing"));
   if(a.getTitle()!=null && titles.getOrDefault(a.getTitle().trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT),List.of()).size()>1)
    findings.add(new QualityFinding(a.getId(),a.getTitle(),"DUPLICATE_TITLE","LOW","Review similarly titled articles and consolidate if redundant"));
   if(a.getStatus()==ArticleStatus.PUBLISHED && (a.getActiveVersion()==null || a.getCategory()==null))
    findings.add(new QualityFinding(a.getId(),a.getTitle(),"MISSING_CONTENT_METADATA","HIGH","Add an active version and category"));
  }
  findings.sort(Comparator.comparing(QualityFinding::severity).thenComparing(QualityFinding::title,String.CASE_INSENSITIVE_ORDER));
  return new QualityReport(all.size(),findings.size(),Instant.now(),List.copyOf(findings));
 }

 @GetMapping("/connectors/catalog")
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
 public List<ConnectorOption> catalog() {
  return List.of(
   new ConnectorOption("documents","PDF and Word files","UPLOAD","AVAILABLE","Upload PDF or DOCX using existing ingestion tools"),
   new ConnectorOption("url","Web page","URL","AVAILABLE","Import a public URL"),
   new ConnectorOption("website","Website crawler","WEBSITE","AVAILABLE","Import website pages with configured limits"),
   new ConnectorOption("google-drive","Google Drive","OAUTH","SETUP_REQUIRED","Use the operational connector marketplace"),
   new ConnectorOption("confluence","Confluence","OAUTH","SETUP_REQUIRED","Use the operational connector marketplace"),
   new ConnectorOption("notion","Notion","OAUTH","SETUP_REQUIRED","Use the operational connector marketplace"),
   new ConnectorOption("sharepoint","SharePoint","OAUTH","SETUP_REQUIRED","Microsoft Graph read-only source import"),
   new ConnectorOption("slack","Slack","OAUTH","SETUP_REQUIRED","Channel messages snapshot import"),
   new ConnectorOption("github","GitHub","OAUTH","SETUP_REQUIRED","Repository README import"),
   new ConnectorOption("jira","Jira","OAUTH","SETUP_REQUIRED","Project issue summary import")
  );
 }
 @GetMapping("/connectors/sources")
 @Transactional(readOnly=true)
 @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
 public List<KnowledgeSourceResponse> connectedSources() {
  return sources.findByTenantIdOrderByCreatedAtDesc(TenantContext.get()).stream().map(KnowledgeSourceResponse::from).toList();
 }
}
