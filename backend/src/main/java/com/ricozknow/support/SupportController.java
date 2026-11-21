package com.ricozknow.support;
import com.ricozknow.common.TenantContext; import jakarta.servlet.http.*; import jakarta.validation.*; import jakarta.validation.constraints.*; import lombok.RequiredArgsConstructor; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/portal/support") @RequiredArgsConstructor
public class SupportController {
 private final SupportTicketService service;
 private final com.ricozknow.common.RateLimiter limiter;
 @PostMapping("/tickets") public TicketResponse create(@RequestParam(required=false) String tenant,@Valid @RequestBody TicketRequest r,HttpServletRequest req){
  String client=req.getRemoteAddr()==null?"unknown":req.getRemoteAddr();
  if(!limiter.allow("support:"+TenantContext.get()+":"+client,5,java.time.Duration.ofMinutes(10)))
   throw new com.ricozknow.common.TooManyRequestsException("Too many support requests");
  String session=cookie(req,"rk_customer_session"); SupportTicket t=service.create(r.email(),r.subject(),r.description(),session,r.conversationId()); return new TicketResponse(t.getId(),t.getStatus(),t.getProvider());
 }
 private String cookie(HttpServletRequest r,String n){if(r.getCookies()!=null)for(Cookie c:r.getCookies())if(n.equals(c.getName()))return c.getValue();return null;}
 public record TicketRequest(@Email @NotBlank String email,@NotBlank @Size(max=500) String subject,@NotBlank @Size(max=10000) String description,UUID conversationId){}
 public record TicketResponse(UUID id,String status,String provider){}
}