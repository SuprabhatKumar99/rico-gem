package com.ricozknow.automation;
import com.ricozknow.common.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AutomationTenantEnqueueTest {
 @AfterEach void clear(){TenantContext.clear();}
 @Test void wrongTenantCannotQueueEvents(){JdbcTemplate db=mock(JdbcTemplate.class);AutomationExecutionService service=new AutomationExecutionService(db,
   mock(org.springframework.transaction.support.TransactionTemplate.class),new ObjectMapper(),mock(com.ricozknow.article.ArticleRepository.class),mock(com.ricozknow.user.UserRepository.class),mock(com.ricozknow.notification.InAppNotificationRepository.class));
  TenantContext.set(UUID.randomUUID());
  assertThrows(SecurityException.class,()->service.enqueue(new AutomationEvent(UUID.randomUUID(),"ARTICLE_UPDATED",UUID.randomUUID(),"e1")));
  verifyNoInteractions(db);
 }
 @Test void rejectsUntrustedTriggerNames(){JdbcTemplate db=mock(JdbcTemplate.class);AutomationExecutionService service=new AutomationExecutionService(db,
   mock(org.springframework.transaction.support.TransactionTemplate.class),new ObjectMapper(),mock(com.ricozknow.article.ArticleRepository.class),mock(com.ricozknow.user.UserRepository.class),mock(com.ricozknow.notification.InAppNotificationRepository.class));
  UUID tenant=UUID.randomUUID();TenantContext.set(tenant);
  assertThrows(IllegalArgumentException.class,()->service.enqueue(new AutomationEvent(tenant,"RUN_SHELL",null,"evil")));
  verifyNoInteractions(db);
 }
}
