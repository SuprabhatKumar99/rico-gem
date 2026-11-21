package com.ricozknow.intelligence;

import com.ricozknow.analytics.AnalyticsDashboardService;
import com.ricozknow.analytics.dto.KnowledgeGapResponse;
import com.ricozknow.article.ArticleService;
import com.ricozknow.article.dto.ArticleCreateRequest;
import com.ricozknow.intelligence.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class KnowledgeIntelligenceController {
    private final AiKnowledgeService ai;
    private final KnowledgeIntelligenceService intelligence;
    private final KnowledgeGapTicketRepository gapRepository;
    private final AnalyticsDashboardService analytics;
    private final ArticleService articleService;

    @PostMapping("/articles/ai/generate")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public AiGeneratedArticleResponse generate(@Valid @RequestBody AiGenerateRequest request){return ai.generate(request);}

    @PostMapping("/articles/ai/improve")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public Map<String,Object> improve(@Valid @RequestBody AiImproveRequest request){return Map.of("content",ai.improve(request.content(),request.instruction()));}

    @PostMapping("/articles/ai/generate-and-create-draft")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public Map<String,Object> generateAndCreate(@Valid @RequestBody AiGenerateRequest request){
        var generated=ai.generate(request);
        var created=articleService.createDraft(new ArticleCreateRequest(generated.title(),generated.suggestedSlug(),request.categoryId(),generated.content()));
        return Map.of("articleId",created.getId(),"article",com.ricozknow.article.dto.ArticleResponse.from(created),"generated",generated);
    }

    @PostMapping("/articles/{articleId}/intelligence/analyze")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public ArticleIntelligenceResponse analyze(@PathVariable UUID articleId){return intelligence.analyze(articleId);}

    @GetMapping("/articles/{articleId}/intelligence")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public ArticleIntelligenceResponse get(@PathVariable UUID articleId){return intelligence.get(articleId);}

    @PostMapping("/articles/{articleId}/dependencies/detect")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<ArticleDependencyResponse> detect(@PathVariable UUID articleId){
        intelligence.analyze(articleId);
        return intelligence.dependencies(articleId);
    }

    @GetMapping("/articles/{articleId}/dependencies")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<ArticleDependencyResponse> dependencies(@PathVariable UUID articleId){return intelligence.dependencies(articleId);}

    @GetMapping("/articles/{articleId}/dependents")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<ArticleDependencyResponse> dependents(@PathVariable UUID articleId){return intelligence.dependents(articleId);}

    @GetMapping("/admin/knowledge-gaps")
    @PreAuthorize("hasRole('ADMIN')")
    public List<KnowledgeGapTicketResponse> gaps(@RequestParam(defaultValue="30") int days,@RequestParam(defaultValue="20") int limit){
        List<KnowledgeGapResponse> source=analytics.knowledgeGaps(days,limit);
        List<KnowledgeGapTicketResponse> out=new ArrayList<>();
        for(KnowledgeGapResponse g:source){
            KnowledgeGapTicket t=intelligence.upsertGap(g.query(),g.volumeCount(),g.zeroResultCount(),g.unsuccessfulCount(),g.gapScore());
            if(t.getSuggestedTitle()==null || t.getSuggestedTitle().isBlank()){
                var generated=ai.suggestForGap(t.getQueryText()); t.setSuggestedTitle(generated.title());
                t.setSuggestedOutline(toJson(generated.outline())); t.setSuggestedKeywords(toJson(generated.keywords())); gapRepository.save(t);
            }
            out.add(intelligence.gap(t));
        }
        return out;
    }

    @PostMapping("/admin/knowledge-gaps/{id}/generate-suggestion")
    @PreAuthorize("hasRole('ADMIN')")
    public KnowledgeGapTicketResponse generateSuggestion(@PathVariable UUID id){
        KnowledgeGapTicket t=gapRepository.findByTenantIdAndId(com.ricozknow.common.TenantContext.get(),id).orElseThrow();
        var generated=ai.suggestForGap(t.getQueryText()); t.setSuggestedTitle(generated.title());t.setSuggestedOutline(toJson(generated.outline()));t.setSuggestedKeywords(toJson(generated.keywords()));gapRepository.save(t);return intelligence.gap(t);
    }

    @PostMapping("/admin/knowledge-gaps/{id}/create-draft")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> createGapDraft(@PathVariable UUID id){
        KnowledgeGapTicket t=gapRepository.findByTenantIdAndId(com.ricozknow.common.TenantContext.get(),id).orElseThrow();
        var generated=ai.suggestForGap(t.getQueryText()); var article=articleService.createDraft(new ArticleCreateRequest(generated.title(),generated.suggestedSlug(),null,generated.content())); t.setArticleId(article.getId());t.setStatus("RESOLVED");gapRepository.save(t);return Map.of("articleId",article.getId(),"article",com.ricozknow.article.dto.ArticleResponse.from(article));
    }
    private String toJson(Object o){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);}catch(Exception e){return "[]";}}
}
