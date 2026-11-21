package com.ricozknow.search;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.rag.RagRetrievalService;
import com.ricozknow.rag.dto.RagSearchRequest;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Secure, on-demand federation over tenant-local article and connector stores.
 * Provider ACLs are not imported yet: connector docs are ADMIN_ONLY, never customer/RAG data.
 */
@Service
@RequiredArgsConstructor
public class EnterpriseRetrievalService {
    private final JdbcTemplate db;
    private final RagRetrievalService rag;
    @Value("${ricozknow.enterprise-search.hybrid-enabled:false}")
    private boolean hybridEnabled;

    public record Query(String query, Integer limit, String provider, String language,
                        UUID categoryId, Instant from, Instant to, Boolean articles, Boolean connectors) {
        public int maxResults() { return limit == null ? 15 : Math.min(40, Math.max(1, limit)); }
    }
    public record Hit(String id, String sourceType, String title, String excerpt, String url,
                      String citation, String accessLevel, String trust, String freshness,
                      Instant modifiedAt, double score) {}
    public record Result(String query, List<Hit> results, long latencyMs, int articleMatches,
                         int connectorMatches, boolean unanswered) {}
    public record Insights(long searches, long unanswered, double averageLatencyMs, List<Trend> recentDays) {}
    public record Trend(String day, long total, long unanswered) {}
    private record Candidate(String id, String type, String title, String text, String url,
                             Instant modifiedAt, Instant lastSyncAt, String acl, double relevance) {}

    public Result search(Query request) {
        AuthenticatedUser actor = requireTenantAdmin();
        if (request == null || request.query() == null || request.query().isBlank() || request.query().length() > 300)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must contain 1–300 characters");
        if (request.from() != null && request.to() != null && request.from().isAfter(request.to()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        if (request.language() != null && request.language().length() > 16)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid language");
        String query = request.query().trim();
        String provider = request.provider() == null ? "" : request.provider().trim().toLowerCase(Locale.ROOT);
        if (!provider.isEmpty() && !provider.matches("[a-z0-9_-]{1,32}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid provider");
        long start = System.nanoTime();
        UUID tenant = TenantContext.get();
        List<Candidate> candidates = new ArrayList<>();
        int fetchLimit = Math.min(100, request.maxResults() * 3);

        // Articles are matched only through active published versions; no stale search-index grants.
        if (!Boolean.FALSE.equals(request.articles()) && (provider.isEmpty() || provider.equals("article"))) {
            candidates.addAll(db.query("""
                SELECT a.id::text, a.title, a.slug, left(v.content::text, 800), a.updated_at,
                       ts_rank_cd(to_tsvector('simple', coalesce(a.title,'') || ' ' || left(v.content::text,100000)), plainto_tsquery('simple', ?)) AS rank
                FROM articles a JOIN article_versions v ON v.id=a.active_version_id AND v.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.status='PUBLISHED' AND v.status='PUBLISHED'
                  AND to_tsvector('simple', coalesce(a.title,'') || ' ' || left(v.content::text,100000)) @@ plainto_tsquery('simple', ?)
                  AND (?::text IS NULL OR a.language=?)
                  AND (?::uuid IS NULL OR a.category_id=?)
                  AND (?::timestamptz IS NULL OR a.updated_at >= ?)
                  AND (?::timestamptz IS NULL OR a.updated_at <= ?)
                ORDER BY rank DESC, a.updated_at DESC LIMIT ?
                """, (rs, i) -> new Candidate(rs.getString(1),"ARTICLE",rs.getString(2),rs.getString(4),
                        "/portal/article/"+rs.getString(3),instant(rs,5),null,"STAFF_ADMIN",rs.getDouble(6)),
                    query,tenant,query, blankToNull(request.language()),blankToNull(request.language()),
                    request.categoryId(),request.categoryId(),sqlTime(request.from()),sqlTime(request.from()),sqlTime(request.to()),sqlTime(request.to()),fetchLimit));
        }
        // Provider ACL synchronization is not complete: only internally ADMIN_ONLY content is exposed,
        // and only to an authenticated, same-tenant, enabled admin. PRINCIPALS is deny-by-default.
        if (!Boolean.FALSE.equals(request.connectors()) && (provider.isEmpty() || !provider.equals("article"))) {
            candidates.addAll(db.query("""
                SELECT d.id::text,d.title,left(d.content_text,800),d.source_url,d.remote_modified_at,
                       s.last_synced_at,s.provider,
                       ts_rank_cd(d.search_vector,plainto_tsquery('simple',?)) AS rank
                FROM connector_document d JOIN connector_source s ON s.id=d.source_id AND s.tenant_id=d.tenant_id
                WHERE d.tenant_id=? AND d.deleted_at IS NULL AND s.disabled=false
                  AND d.acl_mode='ADMIN_ONLY'
                  AND (?::text IS NULL OR s.provider=?)
                  AND (?::timestamptz IS NULL OR d.remote_modified_at>=?)
                  AND (?::timestamptz IS NULL OR d.remote_modified_at<=?)
                  AND d.search_vector @@ plainto_tsquery('simple',?)
                ORDER BY rank DESC,d.updated_at DESC LIMIT ?
                """, (rs,i) -> new Candidate(rs.getString(1),rs.getString(7).toUpperCase(Locale.ROOT),rs.getString(2),
                            rs.getString(3),safeUrl(rs.getString(4)),instant(rs,5),instant(rs,6),"ADMIN_ONLY",rs.getDouble(8)),
                    query,tenant, provider.isEmpty()?null:provider, provider.isEmpty()?null:provider,
                    sqlTime(request.from()),sqlTime(request.from()),sqlTime(request.to()),sqlTime(request.to()),query,fetchLimit));
        }
        // Optional semantic retrieval over PUBLIC articles only; RagRetrievalService performs
        // an independent live PostgreSQL permission/active-version check on every chunk.
        // Connector documents are deliberately never added to the vector index without ACLs.
        if (hybridEnabled && !Boolean.FALSE.equals(request.articles()) && (provider.isEmpty() || provider.equals("article"))) {
            try {
                var semantic=rag.retrieve(new RagSearchRequest(query, fetchLimit, request.categoryId(),
                        Set.of("PUBLIC"),null,blankToNull(request.language()),Math.min(100,fetchLimit * 2)));
                for(var hit:semantic.results()) {
                    if(hit.articleId()!=null) {
                        candidates.add(new Candidate(hit.articleId().toString(),"ARTICLE",hit.title(),hit.content(),
                                "/portal/article/"+hit.slug(),parseTimestamp(hit.publishedAt()),null,
                                "STAFF_ADMIN",hit.score()));
                    }
                }
            } catch(RuntimeException ignored) {
                // Vector provider unavailable. Lexical search remains functional and permission-gated.
            }
        }
        // Deduplicate lexical/vector citations by object; keep the best scoring authorized result.
        Map<String,Candidate> dedup=new LinkedHashMap<>();
        for(Candidate c:candidates)dedup.merge(c.type()+":"+c.id(),c,(a,b)->a.relevance()>=b.relevance()?a:b);
        candidates=new ArrayList<>(dedup.values());
        long articleCount = candidates.stream().filter(c -> c.type().equals("ARTICLE")).count();
        long connectorCount = candidates.size()-articleCount;
        // Combined lexical and optional semantic scores rank candidates only after authorization.
        Map<String,Integer> userClicks=new HashMap<>();
        db.query("select hit_id,click_count from enterprise_search_feedback where tenant_id=? and actor_id=?",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> userClicks.put(rs.getString(1),rs.getInt(2)), tenant,actor.userId());
        // The user's own previous source openings provide a modest bounded personalization boost.
        candidates.sort(Comparator.comparingDouble((Candidate c) ->
                c.relevance() + Math.min(0.15,0.025*Math.log1p(userClicks.getOrDefault(c.id(),0)))).reversed());
        List<Hit> hits = new ArrayList<>();
        for (Candidate candidate : candidates.stream().limit(request.maxResults()).toList()) {
            boolean article = candidate.type().equals("ARTICLE");
            String freshness = candidate.lastSyncAt() == null && !article ? "NEVER_SYNCED" :
                    candidate.lastSyncAt() != null && Duration.between(candidate.lastSyncAt(),Instant.now()).toDays()>7 ? "STALE" : "CURRENT";
            String trust = article ? "PUBLISHED_ACTIVE_VERSION" : "PROVIDER_ACL_UNVERIFIED";
            hits.add(new Hit(candidate.id(),candidate.type(),candidate.title(),plainSnippet(candidate.text()),candidate.url(),
                    "["+(hits.size()+1)+"]",candidate.acl(),trust,freshness,candidate.modifiedAt(),candidate.relevance()+
                    Math.min(0.15,0.025*Math.log1p(userClicks.getOrDefault(candidate.id(),0)))));
        }
        long elapsed = (System.nanoTime()-start)/1_000_000;
        // Analytics are tenant-scoped and default to a query fingerprint, never raw private text.
        db.update("insert into enterprise_search_event(id,tenant_id,actor_id,query_hash,result_count,article_count,connector_count,latency_ms) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),tenant,actor.userId(),fingerprint(query),hits.size(),articleCount,connectorCount,elapsed);
        return new Result(query,List.copyOf(hits),elapsed,(int)articleCount,(int)connectorCount,hits.isEmpty());
    }

    public void feedback(String id, String sourceType) {
        AuthenticatedUser actor=requireTenantAdmin();
        if(id==null || !id.matches("[0-9a-fA-F-]{36}") || sourceType==null || sourceType.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid source reference");
        UUID docId;
        try {docId=UUID.fromString(id);} catch(IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid source reference");
        }
        UUID tenant=TenantContext.get();
        int authorized;
        if("ARTICLE".equalsIgnoreCase(sourceType)) {
            authorized=db.queryForObject("select count(*) from articles a join article_versions v on v.id=a.active_version_id and v.tenant_id=a.tenant_id where a.tenant_id=? and a.id=? and a.status='PUBLISHED' and v.status='PUBLISHED'",Integer.class,tenant,docId);
        } else {
            authorized=db.queryForObject("select count(*) from connector_document d join connector_source s on s.id=d.source_id and s.tenant_id=d.tenant_id where d.tenant_id=? and d.id=? and d.deleted_at is null and s.disabled=false and d.acl_mode='ADMIN_ONLY' and upper(s.provider)=?",Integer.class,tenant,docId,sourceType.toUpperCase(Locale.ROOT));
        }
        if(authorized!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Source is unavailable");
        db.update("""
                insert into enterprise_search_feedback(tenant_id,actor_id,hit_id,source_type,click_count,last_clicked_at)
                values(?,?,?,?,1,now())
                on conflict(tenant_id,actor_id,hit_id) do update set click_count=least(enterprise_search_feedback.click_count+1,10000),last_clicked_at=now()
                """,tenant,actor.userId(),id,sourceType.toUpperCase(Locale.ROOT));
    }

    public Insights insights() {
        requireTenantAdmin();
        UUID tenant=TenantContext.get();
        Map<String,Number> summary=db.queryForObject("select count(*) as total, count(*) filter(where result_count=0) as misses, coalesce(avg(latency_ms),0) as latency from enterprise_search_event where tenant_id=?",
                (rs,i)->Map.of("total",rs.getLong(1),"misses",rs.getLong(2),"latency",rs.getDouble(3)),tenant);
        List<Trend> days=db.query("select created_at::date::text,count(*),count(*) filter(where result_count=0) from enterprise_search_event where tenant_id=? and created_at>now()-interval '30 days' group by created_at::date order by created_at::date desc",
                (rs,i)->new Trend(rs.getString(1),rs.getLong(2),rs.getLong(3)),tenant);
        return new Insights(summary.get("total").longValue(),summary.get("misses").longValue(),summary.get("latency").doubleValue(),days);
    }

    public static AuthenticatedUser requireTenantAdmin() {
        Object p=SecurityContextHolder.getContext().getAuthentication()==null ? null :
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(p instanceof AuthenticatedUser actor) || !actor.enabled() || !actor.tenantId().equals(TenantContext.get())
                || !actor.roleNames().contains("ADMIN"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Tenant administrator required");
        return actor;
    }
    private static Instant parseTimestamp(String value) {
        try {return value==null?null:Instant.parse(value);} catch(Exception ex){return null;}
    }
        private static String blankToNull(String v) { return v==null||v.isBlank()?null:v.trim(); }
    private static java.sql.Timestamp sqlTime(Instant i) {return i==null?null:java.sql.Timestamp.from(i);}
    private static Instant instant(java.sql.ResultSet rs,int col) throws java.sql.SQLException {
        java.sql.Timestamp t=rs.getTimestamp(col); return t==null?null:t.toInstant();
    }
    static String safeUrl(String url) {
        try { URI uri=URI.create(url); String s=uri.getScheme(); return ("https".equalsIgnoreCase(s)||"http".equalsIgnoreCase(s))&&uri.getHost()!=null?url:null; }
        catch(Exception ex){return null;}
    }
    static String plainSnippet(String text) {
        if(text==null)return "";
        String cleaned=text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("[\\s\\\"{}\\[\\]]+"," ").trim();
        return cleaned.substring(0,Math.min(450,cleaned.length()));
    }
    static String fingerprint(String text) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
}
