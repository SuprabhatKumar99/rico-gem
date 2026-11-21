package com.ricozknow.support;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/portal/conversations") @RequiredArgsConstructor
public class CustomerConversationController {
 private final CustomerConversationService conversations;
 @GetMapping public List<Map<String,Object>> list(@CookieValue(name="rk_customer_session",required=false) String token){return conversations.list(token);}
 @GetMapping("/{id}/messages") public List<Map<String,Object>> messages(@PathVariable UUID id,@CookieValue(name="rk_customer_session",required=false) String token){return conversations.messages(token,id);}
}
