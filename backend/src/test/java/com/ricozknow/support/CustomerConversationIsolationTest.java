package com.ricozknow.support;
import com.ricozknow.common.TenantContext;
import com.ricozknow.customer.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
class CustomerConversationIsolationTest {
 @AfterEach void clear(){TenantContext.clear();}
 @Test void anonymousChatsAreNotPersisted(){JdbcTemplate db=mock(JdbcTemplate.class);CustomerAuthService auth=mock(CustomerAuthService.class);
  TenantContext.set(UUID.randomUUID());CustomerConversationService service=new CustomerConversationService(db,auth,new ObjectMapper());
  assertNull(service.saveExchange(null,null,"en","question","answer",List.of(),false));verifyNoInteractions(db);
 }
 @Test void historyRequiresCustomerSession(){JdbcTemplate db=mock(JdbcTemplate.class);CustomerAuthService auth=mock(CustomerAuthService.class);
  TenantContext.set(UUID.randomUUID());CustomerConversationService service=new CustomerConversationService(db,auth,new ObjectMapper());
  assertThrows(ResponseStatusException.class,()->service.list(null));verifyNoInteractions(db);
 }
 @Test void cannotContinueUnownedConversation(){JdbcTemplate db=mock(JdbcTemplate.class);CustomerAuthService auth=mock(CustomerAuthService.class);
  TenantContext.set(UUID.randomUUID());CustomerUser user=new CustomerUser();user.setId(UUID.randomUUID());when(auth.current("session")).thenReturn(user);
  UUID conv=UUID.randomUUID();when(db.queryForObject(anyString(),eq(Integer.class),any(),any(),any())).thenReturn(0);
  CustomerConversationService service=new CustomerConversationService(db,auth,new ObjectMapper());
  assertThrows(ResponseStatusException.class,()->service.saveExchange("session",conv,"en","q","a",List.of(),false));
 }
}
