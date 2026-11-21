package com.ricozknow.support;
import com.fasterxml.jackson.databind.*; import com.ricozknow.enterprise.settings.TenantSettingsRepository; import com.ricozknow.common.TenantContext; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Service; import java.net.*; import java.net.http.*; import java.nio.charset.StandardCharsets; import java.util.*;
@Service @RequiredArgsConstructor public class HelpdeskService {
 private final TenantSettingsRepository settings; private final ObjectMapper mapper; private final com.ricozknow.security.SecretEncryptionService secrets;
 public Optional<Result> create(SupportTicket t){
  try{var row=settings.findById(TenantContext.get()).orElse(null); if(row==null)return Optional.empty(); JsonNode c=mapper.readTree(row.getSettingsJson()); String provider=c.path("helpdeskProvider").asText(""); if(provider.isBlank())return Optional.empty();
   String url,auth,body; String subject=t.getSubject(); String desc=t.getDescription()+"\nCustomer: "+t.getEmail();
   HttpRequest.Builder b;
   if("ZENDESK".equalsIgnoreCase(provider)){String sub=c.path("zendeskSubdomain").asText();String user=c.path("zendeskEmail").asText();String token=c.path("zendeskApiToken").asText(); if(!sub.matches("[a-zA-Z0-9-]{1,63}")||user.isBlank()||token.isBlank())return Optional.empty();
   token=secrets.decrypt(token,"helpdesk:"+TenantContext.get()+":ZENDESK");url="https://"+sub+".zendesk.com/api/v2/tickets.json";body=mapper.writeValueAsString(Map.of("ticket",Map.of("subject",subject,"comment",Map.of("body",desc),"requester",Map.of("email",t.getEmail()))));auth=Base64.getEncoder().encodeToString((user+"/token:"+token).getBytes(StandardCharsets.UTF_8));b=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").header("Authorization","Basic "+auth);}
   else if("FRESHDESK".equalsIgnoreCase(provider)){String domain=c.path("freshdeskDomain").asText().replaceFirst("^https?://","").replaceAll("/$","");String key=c.path("freshdeskApiKey").asText();if(!domain.matches("(?i)[a-z0-9-]{1,63}\\.freshdesk\\.com")||key.isBlank())return Optional.empty();
   key=secrets.decrypt(key,"helpdesk:"+TenantContext.get()+":FRESHDESK");url="https://"+domain+"/api/v2/tickets";body=mapper.writeValueAsString(Map.of("subject",subject,"description",desc,"email",t.getEmail(),"priority",1,"status",2));auth=Base64.getEncoder().encodeToString((key+":X").getBytes(StandardCharsets.UTF_8));b=HttpRequest.newBuilder(URI.create(url)).header("Content-Type","application/json").header("Authorization","Basic "+auth);}
   else return Optional.empty();
   var response=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build().send(b.timeout(java.time.Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()); if(response.statusCode()/100!=2) return Optional.empty(); JsonNode n=mapper.readTree(response.body());String id=n.path("ticket").path("id").asText(n.path("id").asText(""));return Optional.of(new Result(provider.toUpperCase(),id));
  }catch(Exception e){return Optional.empty();}
 }
 public record Result(String provider,String id){}
}