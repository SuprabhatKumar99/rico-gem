package com.ricozknow.search;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnterpriseRetrievalSecurityTest {
    @Mock JdbcTemplate db;
    @Mock com.ricozknow.rag.RagRetrievalService rag;
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}
    private static void auth(UUID tenant,String role){
        var actor=new AuthenticatedUser(UUID.randomUUID(),tenant,"test@example.com",null,Set.of(role),true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
    }
    private static EnterpriseRetrievalService.Query request(String q){return new EnterpriseRetrievalService.Query(q,10,null,null,null,null,null,true,true);}
    @Test void staffWithoutAdminRoleIsDeniedBeforeSql(){
        UUID tenant=UUID.randomUUID();TenantContext.set(tenant);auth(tenant,"EDITOR");
        assertThatThrownBy(()->new EnterpriseRetrievalService(db,rag).search(request("invoice"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }
    @Test void adminFromDifferentTenantDeniedBeforeSql(){
        TenantContext.set(UUID.randomUUID());auth(UUID.randomUUID(),"ADMIN");
        assertThatThrownBy(()->new EnterpriseRetrievalService(db,rag).search(request("policy"))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }
    @Test void invalidFiltersAreRejectedBeforeSql(){
        UUID tenant=UUID.randomUUID();TenantContext.set(tenant);auth(tenant,"ADMIN");
        var service=new EnterpriseRetrievalService(db,rag);
        assertThatThrownBy(()->service.search(request(" "))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.search(new EnterpriseRetrievalService.Query("help",10,"../../../etc",null,null,null,null,true,true))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db);
    }
    @Test void restrictsHtmlAndUnsafeUrls(){
        assertThat(EnterpriseRetrievalService.safeUrl("javascript:alert(1)")).isNull();
        assertThat(EnterpriseRetrievalService.safeUrl("https://example.com/article")).isEqualTo("https://example.com/article");
        assertThat(EnterpriseRetrievalService.plainSnippet("private\u0000  text")).doesNotContain("\u0000");
    }
    @Test void controllerRequiresAdminRole(){
        var annotation=EnterpriseSearchController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("ADMIN");
    }
}
