package com.ricozknow.notification;
import com.ricozknow.article.Article;
import com.ricozknow.article.ArticleStatus;
import com.ricozknow.common.TenantContext;
import com.ricozknow.role.Role;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service @RequiredArgsConstructor
public class ContentNotificationService {
 private final UserRepository users; private final InAppNotificationRepository notifications;
 @Transactional public void notifyReviewParticipants(Article article,String title,String message){
  UUID tenant=TenantContext.get();
  for(User u:users.findAll()) if(tenant.equals(u.getTenantId()) && (hasRole(u,"ADMIN")||hasRole(u,"REVIEWER"))){
    InAppNotification n=new InAppNotification(); n.setUserId(u.getId()); n.setType("CONTENT_REVIEW"); n.setTitle(title); n.setMessage(message); n.setArticleId(article.getId());
    notifications.save(n);
  }
 }
 @Transactional public void notifyAwaitingReview(Article article){
  UUID tenant=TenantContext.get();
  for(User u:users.findAll()) if(tenant.equals(u.getTenantId()) && (hasRole(u,"ADMIN")||hasRole(u,"REVIEWER"))){
    InAppNotification n=new InAppNotification(); n.setUserId(u.getId()); n.setType("REVIEW_REQUEST"); n.setTitle("Article awaiting review"); n.setMessage("\"" + article.getTitle() + "\" is awaiting review."); n.setArticleId(article.getId());
    notifications.save(n);
  }
 }
 private boolean hasRole(User u,String role){return u.getRoles().stream().map(Role::getName).anyMatch(role::equals);}
}
