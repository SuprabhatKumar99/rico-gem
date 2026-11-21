package com.ricozknow.search;

import com.ricozknow.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/** Persistent relevance cases; evaluates results only after the same live ACL checks as search. */
@Service @RequiredArgsConstructor
public class EnterpriseSearchEvaluationService {
    private final JdbcTemplate db;
    private final EnterpriseRetrievalService retrieval;
    private final ObjectMapper json;
    public record Case(UUID id,String question,List<String> expectedIds,Instant createdAt) {}
    public record Evaluation(int cases,int foundAt10,double hitAt10,double mrr,Instant completedAt) {}

    public Case add(String question,List<String> expectedIds) {
        EnterpriseRetrievalService.requireTenantAdmin();
        if(question==null||question.isBlank()||question.length()>300||expectedIds==null||expectedIds.isEmpty()||expectedIds.size()>20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide a question and 1–20 source IDs");
        try {
            UUID id=UUID.randomUUID();
            db.update("insert into enterprise_search_eval_case(id,tenant_id,question,expected_ids) values(?,?,?,?::jsonb)",
                    id,TenantContext.get(),question.trim(),json.writeValueAsString(expectedIds));
            return new Case(id,question.trim(),List.copyOf(expectedIds),Instant.now());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {throw new IllegalStateException(ex);}
    }
    public List<Case> cases() {
        EnterpriseRetrievalService.requireTenantAdmin();
        return db.query("select id,question,expected_ids::text,created_at from enterprise_search_eval_case where tenant_id=? order by created_at desc limit 100",
            (rs,i)->{
                List<String> ids;
                try { ids=json.readValue(rs.getString(3),new TypeReference<List<String>>(){}); }
                catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException(ex);}
                return new Case((UUID)rs.getObject(1),rs.getString(2),ids,rs.getTimestamp(4).toInstant());
            },TenantContext.get());
    }
    public Evaluation run() {
        EnterpriseRetrievalService.requireTenantAdmin();
        List<Case> items=cases();
        int found=0;double reciprocal=0;
        for(Case item:items) {
            var result=retrieval.search(new EnterpriseRetrievalService.Query(item.question(),10,null,null,null,null,null,true,true));
            for(int i=0;i<result.results().size();i++) {
                if(item.expectedIds().contains(result.results().get(i).id())) {found++;reciprocal+=1.0/(i+1);break;}
            }
        }
        return new Evaluation(items.size(),found,items.isEmpty()?0:(double)found/items.size(),
                items.isEmpty()?0:reciprocal/items.size(),Instant.now());
    }
}
