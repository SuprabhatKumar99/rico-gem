package com.ricozknow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.article.*;
import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.security.RetrievalAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpKnowledgeToolsSecurityTest {
    @Mock ArticleRepository repo;
    @Mock RetrievalAccessGuard guard;
    @Mock JdbcTemplate jdbc;
    @Mock AuditService audit;
    @AfterEach void clear(){TenantContext.clear();}
    @Test void returnsOnlyLiveAuthorizedPublicArticleVersions() throws Exception {
        UUID t=UUID.randomUUID();TenantContext.set(t);
        Article allowed=new Article();allowed.setId(UUID.randomUUID());allowed.setTenantId(t);
        allowed.setTitle("Public leaves policy");allowed.setSlug("leave");
        ArticleVersion av=new ArticleVersion();av.setId(UUID.randomUUID());av.setContent(new ObjectMapper().readTree("{\"blocks\":[{\"text\":\"Public leave policy\"}]}"));
        allowed.setActiveVersion(av);
        Article excluded=new Article();excluded.setId(UUID.randomUUID());excluded.setTenantId(t);excluded.setTitle("Public leave secret");
        excluded.setSlug("private");excluded.setActiveVersion(av);
        when(repo.findByTenantIdAndStatusAndVisibility(eq(t),eq(ArticleStatus.PUBLISHED),eq(Visibility.PUBLIC),any(Pageable.class)))
           .thenReturn(new PageImpl<>(List.of(allowed,excluded)));
        when(guard.publicArticle(allowed.getId(),av.getId())).thenReturn(true);
        when(guard.publicArticle(excluded.getId(),av.getId())).thenReturn(false);
        var results=new McpKnowledgeTools(repo,guard,jdbc,audit).search("leave",10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("slug")).isEqualTo("leave");
        verify(repo).findByTenantIdAndStatusAndVisibility(eq(t),eq(ArticleStatus.PUBLISHED),eq(Visibility.PUBLIC),any(Pageable.class));
    }
    @Test void deniesRetrievalWithoutTenantOwnedPublishedArticle(){
        UUID t=UUID.randomUUID(),id=UUID.randomUUID();TenantContext.set(t);
        when(repo.findByTenantIdAndId(t,id)).thenReturn(Optional.empty());
        assertThatThrownBy(()->new McpKnowledgeTools(repo,guard,jdbc,audit).get(id)).isInstanceOf(Exception.class);
        verifyNoInteractions(guard);
    }
    @Test void rejectsUnboundedOrEmptyQueries(){
        assertThatThrownBy(()->McpKnowledgeTools.safeQuery("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->McpKnowledgeTools.safeQuery("x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(McpKnowledgeTools.safeLimit(999)).isEqualTo(10);
    }
}
