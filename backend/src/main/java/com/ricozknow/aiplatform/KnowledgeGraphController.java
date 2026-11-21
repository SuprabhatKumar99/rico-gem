package com.ricozknow.aiplatform;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.*;
import java.time.*;

/** Deterministic graph and review-only deduplication suggestions (never automatic article merging). */
@RestController @RequestMapping("/api/v1/ai-platform/graph") @PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class KnowledgeGraphController {
    private final JdbcTemplate db;
    private static final Pattern LINK=Pattern.compile("https?://[^\\s\\\"<>]+",Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP=Set.of("that","with","from","this","they","have","will","were","into","your","about","what","which","when","them","then","their","there","using","knowledge","article","content","https","http");
    public record ArticleText(UUID id,String title,String content,Instant reviewAt,Instant updatedAt) {}
    private UUID tenant(){return TenantContext.get();}
    private List<ArticleText> articles(){
        return db.query("""
            SELECT a.id,a.title,left(coalesce(v.content::text,''),20000),a.next_review_at,a.updated_at
            FROM articles a JOIN article_versions v ON v.id=a.active_version_id AND v.tenant_id=a.tenant_id
            WHERE a.tenant_id=? AND a.status='PUBLISHED' AND v.status='PUBLISHED'
            ORDER BY a.updated_at DESC LIMIT 300
            """,(rs,i)->new ArticleText((UUID)rs.getObject(1),rs.getString(2),rs.getString(3),
                    instant(rs,4),instant(rs,5)),tenant());
    }
    private static Instant instant(java.sql.ResultSet rs,int column)throws java.sql.SQLException{
        var t=rs.getTimestamp(column);return t==null?null:t.toInstant();
    }
    static Set<String> keywords(String s){
        if(s==null)return Set.of();Set<String> out=new LinkedHashSet<>();
        Matcher m=Pattern.compile("[\\p{L}\\p{N}]{5,}").matcher(s.toLowerCase(Locale.ROOT));
        while(m.find()&&out.size()<1500){String v=m.group();if(!STOP.contains(v))out.add(v);}
        return out;
    }
    static double similarity(Set<String> a,Set<String> b){
        if(a.isEmpty()||b.isEmpty())return 0;
        long shared=a.stream().filter(b::contains).count();
        return (double)shared/(a.size()+b.size()-shared);
    }
    @PostMapping("/rebuild") public Map<String,Integer> rebuild(){
        UUID tenant=tenant();List<ArticleText> articles=articles();
        db.update("DELETE FROM ai_graph_edge WHERE tenant_id=? AND reviewed=false",tenant);
        db.update("DELETE FROM ai_graph_entity WHERE tenant_id=?",tenant);
        int edges=0,entities=0;
        Map<UUID,Set<String>> keywords=new HashMap<>();
        for(ArticleText a:articles){
            Set<String> extracted=keywords(a.title()+" "+a.content());keywords.put(a.id(),extracted);
            for(String word:extracted.stream().limit(25).toList()){
                db.update("INSERT INTO ai_graph_entity(tenant_id,article_id,name,confidence) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",tenant,a.id(),word,0.6);
                entities++;
            }
        }
        for(ArticleText a:articles)for(ArticleText b:articles){
            if(a.id().equals(b.id()))continue;
            boolean mention=b.title()!=null && b.title().length()>=6 && a.content().toLowerCase(Locale.ROOT).contains(b.title().toLowerCase(Locale.ROOT));
            double score=similarity(keywords.get(a.id()),keywords.get(b.id()));
            String relation=mention?"MENTIONS":score>=0.5?"RELATED":null;
            if(relation!=null){
                db.update("INSERT INTO ai_graph_edge(tenant_id,from_article_id,to_article_id,relation,confidence) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
                        tenant,a.id(),b.id(),relation,Math.min(1.0,mention?0.95:score));edges++;
            }
        }
        return Map.of("articles",articles.size(),"entitiesProcessed",entities,"edgesProcessed",edges);
    }
    @GetMapping public Map<String,Object> graph(){
        UUID tenant=tenant();
        return Map.of("nodes",db.queryForList("SELECT a.id,a.title FROM articles a WHERE a.tenant_id=? AND a.status='PUBLISHED' LIMIT 300",tenant),
                "edges",db.queryForList("SELECT from_article_id,to_article_id,relation,confidence,reviewed FROM ai_graph_edge WHERE tenant_id=? LIMIT 1500",tenant),
                "entities",db.queryForList("SELECT article_id,name,confidence FROM ai_graph_entity WHERE tenant_id=? LIMIT 2500",tenant));
    }
    @PostMapping("/quality/scan") public Map<String,Integer> scanQuality(){
        UUID tenant=tenant();List<ArticleText> articles=articles();
        db.update("DELETE FROM ai_quality_proposal WHERE tenant_id=? AND status='OPEN'",tenant);
        int count=0;
        for(int i=0;i<articles.size();i++){
            ArticleText a=articles.get(i);
            if(a.reviewAt()!=null && a.reviewAt().isBefore(Instant.now())){proposal(a.id(),null,"OVERDUE_REVIEW","Article review is overdue",0.9);count++;}
            if(a.updatedAt()!=null && a.updatedAt().isBefore(Instant.now().minus(Duration.ofDays(365)))){
                proposal(a.id(),null,"STALE_CONTENT","Article has not been updated in over one year",0.65);count++;
            }
            int links=0;Matcher matcher=LINK.matcher(a.content());while(matcher.find())links++;
            if(links>0){proposal(a.id(),null,"LINK_REVIEW","Contains "+links+" links requiring verification; no network check performed",0.4);count++;}
            for(int j=i+1;j<articles.size();j++){
                ArticleText b=articles.get(j);
                double score=similarity(keywords(a.title()+" "+a.content()),keywords(b.title()+" "+b.content()));
                if(score>=0.78){proposal(a.id(),b.id(),"DUPLICATE_REVIEW","Similar published content; propose human-reviewed consolidation only",score);count++;}
            }
        }
        return Map.of("articlesScanned",articles.size(),"proposals",count);
    }
    private void proposal(UUID article,UUID other,String category,String detail,double score){
        db.update("INSERT INTO ai_quality_proposal(tenant_id,article_id,related_article_id,category,explanation,score) VALUES(?,?,?,?,?,?)",
            tenant(),article,other,category,detail,score);
    }
    @GetMapping("/quality/proposals") public List<Map<String,Object>> proposals(){
        return db.queryForList("""
            SELECT id,article_id,related_article_id,category,explanation,score,status,created_at
            FROM ai_quality_proposal WHERE tenant_id=? ORDER BY created_at DESC LIMIT 200
            """,tenant());
    }
    public record ReviewRequest(String status){}
    @PostMapping("/quality/proposals/{id}/review") public void review(@PathVariable UUID id,@RequestBody ReviewRequest request){
        String status=request==null?null:request.status();
        if(!Set.of("ACCEPTED","DISMISSED").contains(status))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid review status");
        UUID actor=((com.ricozknow.user.AuthenticatedUser)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).userId();
        if(db.update("UPDATE ai_quality_proposal SET status=?,reviewed_by=?,reviewed_at=now() WHERE tenant_id=? AND id=? AND status='OPEN'",status,actor,tenant(),id)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Proposal not pending");
        // ACCEPTED is approval of a *proposal*, never an automatic merge or publish.
    }
}
