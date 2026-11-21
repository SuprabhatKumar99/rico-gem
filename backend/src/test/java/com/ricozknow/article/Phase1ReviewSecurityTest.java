package com.ricozknow.article;

import com.ricozknow.article.dto.ReviewDecisionRequest;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase1ReviewSecurityTest {
 @Mock ArticleRepository articles;
 @Mock ArticleVersionRepository versions;
 @Mock ArticleReviewDecisionRepository decisions;
 @Mock ArticleBlockCommentRepository comments;
 @Mock com.ricozknow.category.CategoryRepository categories;
 @Mock com.ricozknow.audit.AuditService audit;
 @Mock com.ricozknow.publishing.IndexJobPublisher index;
 @Mock com.ricozknow.notification.ContentNotificationService notifications;
 @Mock com.ricozknow.enterprise.group.GroupRepository groups;
 @Mock org.springframework.context.ApplicationEventPublisher events;
 @Mock ContentValidator validator;
 @InjectMocks ArticleService service;
 UUID tenant,articleId,versionId,author,reviewer;
 Article article;ArticleVersion version;
 @BeforeEach void setup(){
  tenant=UUID.randomUUID();articleId=UUID.randomUUID();versionId=UUID.randomUUID();author=UUID.randomUUID();reviewer=UUID.randomUUID();
  TenantContext.set(tenant);authenticate(reviewer);
  article=new Article();article.setCreatedBy(author);ReflectionTestUtils.setField(article,"id",articleId);
  version=new ArticleVersion();version.setCreatedBy(author);version.setArticle(article);version.setStatus(VersionStatus.IN_REVIEW);ReflectionTestUtils.setField(version,"id",versionId);
 }
 void authenticate(UUID user){AuthenticatedUser principal=new AuthenticatedUser(user,tenant,"reviewer@example.com",null,Set.of("REVIEWER"),true);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));}
 void stubReview(){when(articles.findByTenantIdAndId(tenant,articleId)).thenReturn(Optional.of(article));when(versions.lockForCollaboration(tenant,versionId)).thenReturn(Optional.of(version));}
 @AfterEach void cleanup(){SecurityContextHolder.clearContext();TenantContext.clear();}
 @Test void approvalForScheduleDoesNotPublishImmediately(){
  stubReview();service.approveForScheduling(articleId,versionId,"Ready at 14:00");
  assertThat(version.getStatus()).isEqualTo(VersionStatus.APPROVED);
  assertThat(version.getReviewedBy()).isEqualTo(reviewer);
  verify(decisions).save(argThat(d->d.getDecision().equals("APPROVE_SCHEDULE")&&d.getReviewerId().equals(reviewer)));
  verifyNoInteractions(index,events);
 }
 @Test void unresolvedBlockCommentPreventsApproval(){
  stubReview();when(comments.existsByTenantIdAndVersionIdAndResolvedFalse(tenant,versionId)).thenReturn(true);
  assertThatThrownBy(()->service.review(articleId,versionId,new ReviewDecisionRequest(true,"OK")))
     .isInstanceOf(InvalidArticleStateException.class).hasMessageContaining("Resolve");
  verifyNoInteractions(decisions,index);
 }
 @Test void enterpriseIndependentApprovalCanBeRequired(){
  ReflectionTestUtils.setField(service,"requireIndependentReview",true);authenticate(author);
  stubReview();
  assertThatThrownBy(()->service.approveForScheduling(articleId,versionId,"approved"))
   .isInstanceOf(InvalidArticleStateException.class).hasMessageContaining("Independent");
 }
 @Test void wrongArticleCannotBeApproved(){
  when(articles.findByTenantIdAndId(tenant,articleId)).thenReturn(Optional.of(article));
  when(versions.lockForCollaboration(tenant,versionId)).thenReturn(Optional.of(version));
  version.setArticle(new Article());
  assertThatThrownBy(()->service.review(articleId,versionId,new ReviewDecisionRequest(true,"ok")))
     .isInstanceOf(InvalidArticleStateException.class).hasMessageContaining("Wrong article");
  verifyNoInteractions(decisions);
 }
}
