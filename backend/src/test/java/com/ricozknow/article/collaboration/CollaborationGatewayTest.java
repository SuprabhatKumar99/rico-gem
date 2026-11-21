package com.ricozknow.article.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.article.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollaborationGatewayTest {
    private final ArticleRepository articles=mock(ArticleRepository.class);
    private final ArticleVersionRepository versions=mock(ArticleVersionRepository.class);
    private final ArticleCollaborationStateRepository states=mock(ArticleCollaborationStateRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private final ContentValidator validator=mock(ContentValidator.class);
    private final AuditService audit=mock(AuditService.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private CollaborationGateway gateway;
    private final UUID tenant=UUID.randomUUID(),article=UUID.randomUUID(),version=UUID.randomUUID();
    @BeforeEach void init(){
        gateway=new CollaborationGateway(articles,versions,states,users,redis,mapper,validator,audit);
        ReflectionTestUtils.setField(gateway,"serviceSecret","this-is-a-test-secret-with-over-32-bytes");
        ReflectionTestUtils.setField(gateway,"wsUrl","ws://localhost:1234");
        TenantContext.set(tenant);
    }
    @AfterEach void clear(){TenantContext.clear();}
    @Test void rejectsWrongInternalSecret(){
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
            ()->gateway.checkServiceKey("not-the-secret")).getStatusCode());
    }
    @Test void rejectsReplayedOrMalformedTicket(){
        String room=tenant+":"+article+":"+version;
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
            ()->gateway.authorize("invalid",room)).getStatusCode());
    }
    @Test void tenantWithoutArticleCannotIssueTicket(){
        when(articles.findByTenantIdAndId(tenant,article)).thenReturn(Optional.empty());
        // Without a session, no ticket is minted even if a caller guesses the IDs.
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
            ()->gateway.mint(article,version)).getStatusCode());
    }
    @Test void cannotWriteSubmittedVersion(){
        Article a=new Article();a.setTenantId(tenant);a.setId(article);
        ArticleVersion v=new ArticleVersion();v.setArticle(a);v.setStatus(VersionStatus.IN_REVIEW);
        when(versions.lockForCollaboration(tenant,version)).thenReturn(Optional.of(v));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
            ()->gateway.store(tenant,article,version,new CollaborationGateway.SavePayload("AA==",mapper.createObjectNode()))).getStatusCode());
    }
    @Test void rejectsCrossTenantSnapshot(){
        when(articles.findByTenantIdAndId(tenant,article)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
            ()->gateway.load(tenant,article,version)).getStatusCode());
    }
}
