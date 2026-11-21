package com.ricozknow.aiplatform;

import com.ricozknow.common.TenantContext;
import com.ricozknow.search.EnterpriseRetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Retrieval-only offline evaluation: no hallucinated claims of model-groundedness. */
@RestController @RequestMapping("/api/v1/ai-platform/evaluation") @PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiEvaluationController {
    private final JdbcTemplate db;
    private final EnterpriseRetrievalService retrieval;
    private final AiSafetyPolicy safety;
    public record CaseRequest(String question,UUID expectedArticleId,String expectedPhrase){}
    private UUID tenant(){return TenantContext.get();}
    @PostMapping("/cases") public Map<String,Object> add(@RequestBody CaseRequest request){
        String question=safety.validateInput(request==null?null:request.question(),300);
        if(request.expectedArticleId()==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected article is required");
        Integer found=db.queryForObject("SELECT count(*) FROM articles WHERE tenant_id=? AND id=? AND status='PUBLISHED'",Integer.class,tenant(),request.expectedArticleId());
        if(found==null||found==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected article unavailable");
        UUID id=UUID.randomUUID();
        String phrase=request.expectedPhrase();
        if(phrase!=null&&phrase.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Phrase too long");
        db.update("INSERT INTO ai_eval_case(id,tenant_id,question,expected_article_id,expected_phrase) VALUES(?,?,?,?,?)",
                 id,tenant(),question,request.expectedArticleId(),phrase);
        return Map.of("id",id,"question",question,"expectedArticleId",request.expectedArticleId());
    }
    @GetMapping("/cases") public List<Map<String,Object>> cases(){
        return db.queryForList("SELECT id,question,expected_article_id,expected_phrase FROM ai_eval_case WHERE tenant_id=? ORDER BY created_at DESC LIMIT 100",tenant());
    }
    @PostMapping("/run") public Map<String,Object> run(){
        List<Map<String,Object>> cases=cases();
        if(cases.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Add evaluation cases first");
        int hits=0,grounded=0,cited=0;
        for(Map<String,Object> row:cases){
            long start=System.nanoTime();
            var results=retrieval.search(new EnterpriseRetrievalService.Query((String)row.get("question"),10,"article",null,null,null,null,true,false)).results();
            String expected=Objects.toString(row.get("expected_article_id"),"");
            boolean hit=results.stream().anyMatch(h->h.id().equals(expected));
            boolean citation=results.stream().anyMatch(h->h.id().equals(expected)&&h.citation()!=null&&!h.citation().isBlank());
            String phrase=(String)row.get("expected_phrase");
            boolean supported=hit && (phrase==null||phrase.isBlank()||results.stream().anyMatch(h->h.id().equals(expected)&&h.excerpt().toLowerCase(Locale.ROOT).contains(phrase.toLowerCase(Locale.ROOT))));
            db.update("INSERT INTO ai_eval_result(tenant_id,case_id,hit,cited,grounded,latency_ms) VALUES(?,?,?,?,?,?)",
                tenant(),row.get("id"),hit,citation,supported,(System.nanoTime()-start)/1_000_000);
            if(hit)hits++;if(citation)cited++;if(supported)grounded++;
        }
        int n=cases.size();
        return Map.of("cases",n,"hitAt10",(double)hits/n,"citationPresence",(double)cited/n,
                "evidencePhrasePresent",(double)grounded/n,
                "warning","Evidence phrase match is a lexical proxy, NOT a verified hallucination/groundedness score");
    }
    @GetMapping("/runs")public List<Map<String,Object>> results(){
        return db.queryForList("SELECT case_id,hit,cited,grounded,latency_ms,created_at FROM ai_eval_result WHERE tenant_id=? ORDER BY created_at DESC LIMIT 200",tenant());
    }
}
