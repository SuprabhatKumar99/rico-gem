package com.ricozknow.rag;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.conversation.*;
import com.ricozknow.rag.feedback.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagConversationOwnershipTest {
    @Mock RagConversationRepository conversations;
    @Mock RagMessageRepository messages;
    @Mock AuditService audits;
    @Mock RagFeedbackRepository feedback;
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}
    private static void key(UUID tenant,UUID id) {
        RagApiPrincipal p=new RagApiPrincipal(id,tenant,Set.of("RAG_SEARCH"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    @Test void deniesUnrelatedJwtAndCrossTenantKey() {
        UUID tenant=UUID.randomUUID();TenantContext.set(tenant);
        assertThatThrownBy(RagApiAccess::keyId).isInstanceOf(RagApiException.class);
        key(UUID.randomUUID(),UUID.randomUUID());
        assertThatThrownBy(RagApiAccess::keyId).isInstanceOf(RagApiException.class);
    }
    @Test void conversationsAreQueriedByKeyNotJustTenant() {
        UUID t=UUID.randomUUID(),id=UUID.randomUUID();TenantContext.set(t);key(t,id);
        when(conversations.findAllByTenantIdAndOwnerKeyIdOrderByUpdatedAtDesc(t,id)).thenReturn(List.of());
        assertThat(new RagConversationService(conversations,messages,audits).list()).isEmpty();
        verify(conversations).findAllByTenantIdAndOwnerKeyIdOrderByUpdatedAtDesc(t,id);
        verifyNoInteractions(messages);
    }
    @Test void foreignConversationAndFeedbackAreDenied() {
        UUID t=UUID.randomUUID(),id=UUID.randomUUID(),foreignConversation=UUID.randomUUID();TenantContext.set(t);key(t,id);
        assertThatThrownBy(()->new RagConversationService(conversations,messages,audits).get(foreignConversation))
                .isInstanceOf(RagApiException.class);
        verify(conversations).findByTenantIdAndIdAndOwnerKeyId(t,foreignConversation,id);
        assertThatThrownBy(()->new RagFeedbackService(feedback,conversations,messages,audits)
                .upsert(new RagFeedbackRequest(foreignConversation,null,true,"no"))).isInstanceOf(RagApiException.class);
        verifyNoInteractions(feedback,messages);
    }
}
