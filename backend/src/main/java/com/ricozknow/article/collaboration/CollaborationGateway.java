package com.ricozknow.article.collaboration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.article.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** Short-lived one-use WS tickets + database-authorized snapshot storage.
 * The collaboration service has NO JDBC credentials; all operations pass through this gateway.
 */
@Service @RequiredArgsConstructor
public class CollaborationGateway {
    private final ArticleRepository articles;
    private final ArticleVersionRepository versions;
    private final ArticleCollaborationStateRepository states;
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final ContentValidator validator;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    @Value("${ricozknow.collaboration.internal-secret:}") private String serviceSecret;
    @Value("${ricozknow.collaboration.ws-url:}") private String wsUrl;

    public record Ticket(String token, String room, String websocketUrl, int expiresInSeconds) {}
    public record Authorization(String actorId, String displayName, String room) {}
    public record Loaded(JsonNode content, String state) {}
    public record SavePayload(String state, JsonNode content) {}

    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
    private static String room(UUID tenant, UUID article, UUID version) { return tenant+":"+article+":"+version; }

    private ArticleVersion scopedVersion(UUID tenant, UUID article, UUID version, boolean editable) {
        Article a=articles.findByTenantIdAndId(tenant,article).orElseThrow(CollaborationGateway::notFound);
        ArticleVersion v=versions.findByTenantIdAndId(tenant,version).orElseThrow(CollaborationGateway::notFound);
        if (!v.getArticle().getId().equals(a.getId())) throw notFound();
        if(editable && v.getStatus()!=VersionStatus.DRAFT && v.getStatus()!=VersionStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Collaboration is locked once review begins");
        return v;
    }

    @Transactional(readOnly=true)
    public Ticket mint(UUID article,UUID version) {
        if(wsUrl.isBlank() || serviceSecret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Collaboration server not configured");
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser actor))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if(!actor.enabled() || !(actor.roleNames().contains("ADMIN") || actor.roleNames().contains("EDITOR")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        UUID tenant=TenantContext.get();
        if(!tenant.equals(actor.tenantId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        scopedVersion(tenant,article,version,true);
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String name=room(tenant,article,version);
        // Redis SET key PX ttl NX; ticket cannot be replayed after the first handshake.
        Boolean inserted=redis.opsForValue().setIfAbsent("collab:ticket:"+token,
                name+"|"+actor.userId(), Duration.ofSeconds(60));
        if(!Boolean.TRUE.equals(inserted)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        return new Ticket(token,name,wsUrl,60);
    }

    public void checkServiceKey(String provided) {
        if(serviceSecret==null || serviceSecret.length()<32 || provided==null ||
                !MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    @Transactional(readOnly=true)
    public Authorization authorize(String ticket,String name) {
        if(ticket==null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String stored=redis.opsForValue().getAndDelete("collab:ticket:"+ticket);
        if(stored==null || !stored.startsWith(name+"|")) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String[] parts=name.split(":",-1);
        if(parts.length!=3) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        try {
            UUID tenant=UUID.fromString(parts[0]),article=UUID.fromString(parts[1]),version=UUID.fromString(parts[2]);
            UUID actor=UUID.fromString(stored.substring(name.length()+1));
            User user=users.findById(actor).filter(u->u.getTenantId().equals(tenant))
                    .filter(u->u.getStatus()==User.UserStatus.ACTIVE)
                    .filter(u->u.getRoles().stream().anyMatch(r->"ADMIN".equals(r.getName())||"EDITOR".equals(r.getName())))
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
            scopedVersion(tenant,article,version,true);
            return new Authorization(actor.toString(),user.getEmail(),name);
        } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    }

    @Transactional(readOnly=true)
    public void allowRoom(String name,UUID actorId) {
        String[] parts=name==null?new String[0]:name.split(":",-1);
        if(parts.length!=3) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        try {
            UUID tenant=UUID.fromString(parts[0]),article=UUID.fromString(parts[1]),version=UUID.fromString(parts[2]);
            users.findById(actorId).filter(u->u.getTenantId().equals(tenant))
                .filter(u->u.getStatus()==User.UserStatus.ACTIVE)
                .filter(u->u.getRoles().stream().anyMatch(r->"ADMIN".equals(r.getName())||"EDITOR".equals(r.getName())))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
            scopedVersion(tenant,article,version,true);
        }catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
    }

    @Transactional(readOnly=true)
    public Loaded load(UUID tenant,UUID article,UUID version) {
        ArticleVersion v=scopedVersion(tenant,article,version,true);
        String state=states.findByTenantIdAndVersionId(tenant,version)
                .map(s->Base64.getEncoder().encodeToString(s.getYdocState())).orElse(null);
        return new Loaded(v.getContent(),state);
    }

    private static String hash(JsonNode node) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(node.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch(Exception e) { throw new IllegalStateException(e); }
    }

    @Transactional
    public void store(UUID tenant,UUID article,UUID version,SavePayload payload) {
        ArticleVersion v=versions.lockForCollaboration(tenant,version).orElseThrow(CollaborationGateway::notFound);
        if(!v.getArticle().getId().equals(article) || !v.getArticle().getTenantId().equals(tenant)) throw notFound();
        if(v.getStatus()!=VersionStatus.DRAFT && v.getStatus()!=VersionStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Draft was submitted for review");
        if(payload==null || payload.content()==null || payload.state()==null || payload.state().length()>5_600_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid collaboration payload");
        byte[] binary;
        try { binary=Base64.getDecoder().decode(payload.state()); }
        catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Yjs state");}
        if(binary.length<1 || binary.length>4_194_304) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        if(payload.content().toString().getBytes(StandardCharsets.UTF_8).length>2_000_000)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        JsonNode rich=payload.content().path("tiptap");
        if(!"doc".equals(rich.path("type").asText()) || !rich.path("content").isArray())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Expected TipTap document JSON");
        ArticleCollaborationState snapshot=states.findByTenantIdAndVersionId(tenant,version).orElseGet(()->{
            ArticleCollaborationState s=new ArticleCollaborationState();
            s.setTenantId(tenant); s.setArticleId(article); s.setVersionId(version);
            s.setMirroredSha256(hash(v.getContent()));
            return s;
        });
        // External writes through the old draft API must not be silently overwritten.
        if(!snapshot.getMirroredSha256().equals(hash(v.getContent())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Draft was changed outside collaboration");
        snapshot.setYdocState(binary);
        // Preserve unrelated metadata and the legacy projection required by the portal.
        com.fasterxml.jackson.databind.node.ObjectNode projected=(com.fasterxml.jackson.databind.node.ObjectNode)v.getContent().deepCopy();
        projected.set("blocks",payload.content().path("blocks").deepCopy());
        projected.set("tiptap",rich.deepCopy());
        validator.validate(projected);
        v.setContent(projected);
        v.setChangeSummary("Yjs collaborative autosave");
        v.setStatus(VersionStatus.DRAFT);
        snapshot.setMirroredSha256(hash(v.getContent()));
        states.save(snapshot);
        // Do not log document contents or binary Yjs updates into immutable audit history.
        audit.recordForTenant(tenant,"ArticleVersion",version,"COLLABORATION_SNAPSHOT",null,
                Map.of("sha256",snapshot.getMirroredSha256(),"bytes",binary.length));
    }
}
