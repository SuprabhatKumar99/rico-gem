package com.ricozknow.article;
import com.ricozknow.article.dto.*;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.User;
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
class Phase1GovernanceTest {
 @Mock ArticleRepository articles;@Mock ArticleVersionRepository versions;
 @Mock ArticleReviewDecisionRepository decisions;@Mock ArticleVerificationEventRepository events;
 @Mock ArticleBlockCommentRepository comments;@Mock com.ricozknow.user.UserRepository users;
 @Mock com.ricozknow.audit.AuditService audit;
 @InjectMocks ArticleGovernanceService governance;
 UUID tenant,articleId;Article article;
 @BeforeEach void setup(){tenant=UUID.randomUUID();articleId=UUID.randomUUID();TenantContext.set(tenant);
  article=new Article();ReflectionTestUtils.setField(article,"id",articleId);
  lenient().when(articles.findByTenantIdAndId(tenant,articleId)).thenReturn(Optional.of(article));
 }
 @AfterEach void cleanup(){TenantContext.clear();}
 @Test void otherTenantUserCannotOwnArticle(){
  UUID userId=UUID.randomUUID();User outsider=new User();outsider.setTenantId(UUID.randomUUID());
  when(users.findById(userId)).thenReturn(Optional.of(outsider));
  assertThatThrownBy(()->governance.update(articleId,new GovernanceRequest(userId,null)))
   .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("this tenant");
 }
 @Test void verificationCannotIgnoreContentWarnings(){
  article.setNextReviewAt(Instant.now().minusSeconds(10));
  assertThatThrownBy(()->governance.verify(articleId))
     .isInstanceOf(InvalidArticleStateException.class).hasMessageContaining("Quality checks failed");
  verifyNoInteractions(events);
 }
 @Test void knowledgeHealthCountsOverdueArticles(){
  Article other=new Article();other.setNextReviewAt(Instant.now().plusSeconds(3600));other.setStatus(ArticleStatus.PUBLISHED);
  when(articles.findAllByTenantId(tenant)).thenReturn(List.of(article,other));
  var result=governance.health();assertThat(result.total()).isEqualTo(2);
  assertThat(result.dueForReview()).isEqualTo(1);assertThat(result.published()).isEqualTo(1);
 }
}
