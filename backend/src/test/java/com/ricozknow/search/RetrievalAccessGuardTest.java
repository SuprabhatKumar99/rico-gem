package com.ricozknow.search;

import com.ricozknow.article.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.RagChunkDocument;
import com.ricozknow.security.RetrievalAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetrievalAccessGuardTest {
    @Mock ArticleRepository articles;
    @AfterEach void clear(){TenantContext.clear();}
    private static RagChunkDocument chunk(UUID tenant,UUID article,UUID version,String visibility,String source){
        return new RagChunkDocument(tenant.toString(),article.toString(),version.toString(),"c1","slug","Title","private excerpt",null,null,visibility,null,0,List.of(0.1f),source,null,null,"en");
    }
    @Test void deniesExternalSourcesEvenWhenIndexClaimsPublic(){
        UUID t=UUID.randomUUID();TenantContext.set(t);
        var gate=new RetrievalAccessGuard(articles);
        assertThat(gate.publicChunk(chunk(t,UUID.randomUUID(),UUID.randomUUID(),"PUBLIC","external"))).isFalse();
        verifyNoInteractions(articles);
    }
    @Test void deniesCrossTenantChunkBeforeQueryingArticle(){
        UUID t=UUID.randomUUID();TenantContext.set(t);
        assertThat(new RetrievalAccessGuard(articles).publicChunk(chunk(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"PUBLIC",null))).isFalse();
        verifyNoInteractions(articles);
    }
    @Test void deniesPrivateAndStaleVersions(){
        UUID t=UUID.randomUUID(),id=UUID.randomUUID(),ver=UUID.randomUUID();TenantContext.set(t);
        Article a=new Article();a.setTenantId(t);a.setStatus(ArticleStatus.PUBLISHED);a.setVisibility(Visibility.PRIVATE);
        ArticleVersion v=new ArticleVersion();v.setId(ver);v.setStatus(VersionStatus.PUBLISHED);a.setActiveVersion(v);
        when(articles.findByTenantIdAndId(t,id)).thenReturn(Optional.of(a));
        var gate=new RetrievalAccessGuard(articles);
        assertThat(gate.publicChunk(chunk(t,id,ver,"PUBLIC",null))).isFalse();
        a.setVisibility(Visibility.AUTHENTICATED);
        assertThat(gate.publicChunk(chunk(t,id,ver,"PUBLIC",null))).isFalse();
        assertThat(gate.portalArticle(id,ver,true)).isTrue();
        assertThat(gate.portalArticle(id,ver,false)).isFalse();
        a.setVisibility(Visibility.PUBLIC);
        assertThat(gate.publicChunk(chunk(t,id,UUID.randomUUID(),"PUBLIC",null))).isFalse();
        assertThat(gate.publicChunk(chunk(t,id,ver,"PUBLIC",null))).isTrue();
        a.setStatus(ArticleStatus.ARCHIVED);
        assertThat(gate.publicChunk(chunk(t,id,ver,"PUBLIC",null))).isFalse();
    }
    @Test void deniesUserRequestedAuthenticatedIndexScope(){
        UUID t=UUID.randomUUID();TenantContext.set(t);
        assertThat(new RetrievalAccessGuard(articles).publicChunk(chunk(t,UUID.randomUUID(),UUID.randomUUID(),"AUTHENTICATED",null))).isFalse();
        verifyNoInteractions(articles);
    }
}
