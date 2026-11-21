package com.ricozknow.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.audit.AuditService;
import com.ricozknow.security.RetrievalAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Only active published PUBLIC article versions. Never returns connector snapshots or group content. */
@Service @RequiredArgsConstructor
public class McpKnowledgeTools {
    private final ArticleRepository articles;
    private final RetrievalAccessGuard access;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public static String safeQuery(String query) {
        if (query == null || query.isBlank() || query.strip().length() > 500)
            throw new IllegalArgumentException("Query must be between 1 and 500 characters");
        return query.strip();
    }
    public static int safeLimit(Integer n) { return n == null ? 5 : Math.max(1,Math.min(10,n)); }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> search(String query, Integer limit) {
        String q=safeQuery(query).toLowerCase(Locale.ROOT);
        int count=safeLimit(limit);
        List<Match> matches=new ArrayList<>();
        // Bounded fallback search. Full public-index retrieval can replace this without changing tool schema.
        // Never use the index alone as a permission authority.
        for(int pageNumber=0;pageNumber<10;pageNumber++) {
            Page<Article> page=articles.findByTenantIdAndStatusAndVisibility(TenantContext.get(),
                    ArticleStatus.PUBLISHED, Visibility.PUBLIC,
                    PageRequest.of(pageNumber,100,Sort.by("id")));
            for(Article a:page.getContent()) {
                if (a.getActiveVersion()==null || !access.publicArticle(a.getId(),a.getActiveVersion().getId())) continue;
                String content=plain(a.getActiveVersion().getContent());
                String title=a.getTitle()==null?"":a.getTitle();
                int score=score(title,q)*5+score(content,q);
                if(score<=0) continue;
                String snippet=snippet(content,q,450);
                matches.add(new Match(score,Map.of("articleId",a.getId().toString(),
                        "versionId",a.getActiveVersion().getId().toString(),
                        "title",title,"slug",a.getSlug(),"snippet",snippet,
                        "url","/portal/article/"+a.getSlug(),"sourceType","published_article")));
            }
            if(!page.hasNext())break;
        }
        return matches.stream().sorted(Comparator.comparingInt(Match::score).reversed())
                .limit(count).map(Match::value).toList();
    }
    @Transactional(readOnly = true)
    public Map<String,Object> get(UUID id) {
        Article a=articles.findByTenantIdAndId(TenantContext.get(),id)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Article not found"));
        if(a.getActiveVersion()==null || !access.publicArticle(id,a.getActiveVersion().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Article not found");
        return Map.of("articleId",a.getId().toString(),"versionId",a.getActiveVersion().getId().toString(),
                "title",a.getTitle(),"slug",a.getSlug(),"url","/portal/article/"+a.getSlug(),
                "content",plain(a.getActiveVersion().getContent()),"sourceType","published_article");
    }
    @Transactional
    public Map<String,Object> requestReview(UUID articleId, UUID keyId, String reason) {
        if(reason==null || reason.isBlank() || reason.length()>500)
            throw new IllegalArgumentException("Reason must be between 1 and 500 characters");
        get(articleId); // live tenant/public/version authorization check
        UUID id=UUID.randomUUID();
        jdbc.update("insert into mcp_review_requests (id,tenant_id,article_id,api_key_id,reason,status) values (?,?,?,?,?,'OPEN')",
                id,TenantContext.get(),articleId,keyId,reason.strip());
        audit.record("McpReviewRequest",id,"MCP_REVIEW_REQUESTED",null,Map.of("articleId",articleId.toString(),"apiKeyId",keyId.toString()));
        return Map.of("requestId",id.toString(),"articleId",articleId.toString(),"status","OPEN");
    }
    private record Match(int score, Map<String,Object> value) {}
    private static int score(String text,String q) {
        if(text==null || text.isBlank())return 0;
        String haystack=text.toLowerCase(Locale.ROOT);
        int p=haystack.indexOf(q), count=0;
        while(p>=0 && count<20){count++;p=haystack.indexOf(q,p+q.length());}
        return count;
    }
    private static String snippet(String content,String query,int max) {
        if(content.isBlank())return "";
        int index=content.toLowerCase(Locale.ROOT).indexOf(query);
        int start=Math.max(0,index<0?0:index-50);
        return content.substring(start,Math.min(content.length(),start+max));
    }
    /** Only textual leaf values. Skip URL/identifier fields and cap model-exposed text. */
    public static String plain(JsonNode content){
        StringBuilder out=new StringBuilder();append(content,out);
        return out.length()>16000?out.substring(0,16000):out.toString().trim();
    }
    private static void append(JsonNode node,StringBuilder out) {
        if(node==null||node.isNull()||out.length()>16000)return;
        if(node.isTextual()){out.append(node.asText()).append(' ');return;}
        if(node.isArray()){for(JsonNode n:node)append(n,out);return;}
        if(node.isObject()){
            for(var it=node.fields();it.hasNext();){var e=it.next();
                if(Set.of("id","url","href","src","type","blockId","class","style","attrs","html").contains(e.getKey()))continue;
                append(e.getValue(),out);
            }
        }
    }
}
