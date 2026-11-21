package com.ricozknow.enterprise.session;
import com.ricozknow.user.AuthenticatedUser; import lombok.RequiredArgsConstructor; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/auth/sessions") @RequiredArgsConstructor public class SessionController { private final SessionService service;
 @GetMapping @PreAuthorize("isAuthenticated()") public List<SessionService.SessionView> list(){return service.list(principal().userId());}
 @DeleteMapping("/{id}") @PreAuthorize("isAuthenticated()") public void revoke(@PathVariable UUID id){service.revoke(principal().userId(),id);}
 private AuthenticatedUser principal(){return (AuthenticatedUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();}
}
