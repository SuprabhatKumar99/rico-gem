package com.ricozknow.widget;
import com.ricozknow.common.TenantContext;
import com.ricozknow.tenant.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class WidgetTenantIsolationTest {
 @AfterEach void clear(){TenantContext.clear();}
 @Test void requestForDifferentTenantIsRejectedBeforeSearch(){
  TenantRepository tenants=mock(TenantRepository.class);var search=mock(com.ricozknow.search.SearchService.class);
  Tenant tenant=new Tenant();tenant.setId(UUID.randomUUID());tenant.setStatus(Tenant.TenantStatus.ACTIVE);
  TenantContext.set(UUID.randomUUID());when(tenants.findBySlug("other")).thenReturn(Optional.of(tenant));
  WidgetChatController controller=new WidgetChatController(search,tenants,mock(com.ricozknow.common.RateLimiter.class),mock(com.ricozknow.ai.AiChatService.class),mock(com.ricozknow.support.CustomerConversationService.class));
  assertThrows(ResponseStatusException.class,()->controller.answer("other",new WidgetChatController.Request("question","session","en",null),null));
  verifyNoInteractions(search);
 }
}
