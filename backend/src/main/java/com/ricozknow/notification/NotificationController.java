package com.ricozknow.notification;
import com.ricozknow.common.TenantContext;
import com.ricozknow.user.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant; import java.util.*; import java.util.stream.*;
@RestController @RequestMapping("/api/v1/notifications") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {
 private final InAppNotificationRepository repo;
 @GetMapping public Map<String,Object> list(){
  UUID user=current(); UUID tenant=TenantContext.get();
  var items=repo.findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(tenant,user).stream().map(n->new NotificationResponse(n.getId(),n.getType(),n.getTitle(),n.getMessage(),n.getArticleId(),n.getVersionId(),n.isRead(),n.getCreatedAt())).toList();
  return Map.of("items",items,"unreadCount",repo.countByTenantIdAndUserIdAndReadFalse(tenant,user));
 }
 @PostMapping("/{id}/read") public void read(@PathVariable UUID id){var n=repo.findByTenantIdAndId(TenantContext.get(),id).orElseThrow(); if(!n.getUserId().equals(current())) throw new org.springframework.security.access.AccessDeniedException("Not yours"); n.setRead(true); n.setReadAt(Instant.now());}
 @PostMapping("/read-all") public void readAll(){repo.findTop50ByTenantIdAndUserIdOrderByCreatedAtDesc(TenantContext.get(),current()).forEach(n->{n.setRead(true);n.setReadAt(Instant.now());});}
 private UUID current(){var a=SecurityContextHolder.getContext().getAuthentication(); if(a!=null&&a.getPrincipal() instanceof AuthenticatedUser u)return u.userId(); throw new IllegalStateException("No authenticated user");}
}
