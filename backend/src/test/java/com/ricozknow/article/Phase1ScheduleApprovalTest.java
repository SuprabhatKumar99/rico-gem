package com.ricozknow.article;
import com.ricozknow.article.dto.ScheduleRequest;
import com.ricozknow.common.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class Phase1ScheduleApprovalTest {
 @Mock ArticleRepository articles;@Mock ArticleVersionRepository versions;
 @Mock ArticleTagRepository tags;@Mock ArticleRelatedRepository related;
 @Mock ArticleBlockCommentRepository comments;@Mock com.ricozknow.audit.AuditService audit;
 @Mock com.ricozknow.notification.ContentNotificationService notifications;
 @Mock ArticleService articlesService;
 @InjectMocks ArticleWorkflowService workflow;
 UUID tenant,articleId,versionId; Article article;ArticleVersion version;
 @BeforeEach void setup(){tenant=UUID.randomUUID();articleId=UUID.randomUUID();versionId=UUID.randomUUID();TenantContext.set(tenant);
  article=new Article();ReflectionTestUtils.setField(article,"id",articleId);
  version=new ArticleVersion();version.setArticle(article);ReflectionTestUtils.setField(version,"id",versionId);
  when(versions.findByTenantIdAndId(tenant,versionId)).thenReturn(Optional.of(version));
 }
 @AfterEach void clear(){TenantContext.clear();}
 @Test void submittingReviewIsNotSufficientToSchedule(){
  version.setStatus(VersionStatus.IN_REVIEW);
  assertThatThrownBy(()->workflow.schedulePublish(articleId,versionId,new ScheduleRequest(Instant.now().plusSeconds(7200))))
     .isInstanceOf(InvalidArticleStateException.class).hasMessageContaining("approval");
  verify(articles,never()).save(any());
 }
 @Test void onlyApprovedRevisionGetsScheduled(){
  version.setStatus(VersionStatus.APPROVED);version.setReviewedBy(UUID.randomUUID());
  when(articles.findByTenantIdAndId(tenant,articleId)).thenReturn(Optional.of(article));
  when(versions.findByTenantIdAndArticleIdOrderByVersionNumberDesc(tenant,articleId)).thenReturn(List.of(version));
  Instant at=Instant.now().plusSeconds(3600);
  workflow.schedulePublish(articleId,versionId,new ScheduleRequest(at));
  assertThat(version.getStatus()).isEqualTo(VersionStatus.SCHEDULED);
  assertThat(article.getScheduledPublishAt()).isEqualTo(at);
 }
 @Test void sameTenantButWrongArticleDoesNotExposeVersion(){
  ReflectionTestUtils.setField(article,"id",UUID.randomUUID());
  assertThatThrownBy(()->workflow.schedulePublish(articleId,versionId,new ScheduleRequest(Instant.now().plusSeconds(1200))))
     .isInstanceOf(InvalidArticleStateException.class);
 }
}
