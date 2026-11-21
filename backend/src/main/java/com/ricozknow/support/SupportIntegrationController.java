package com.ricozknow.support;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.common.TenantContext; 
import com.ricozknow.enterprise.settings.*; 
import lombok.RequiredArgsConstructor; 
import org.springframework.security.access.prepost.PreAuthorize; 
import org.springframework.web.bind.annotation.*; 
import java.util.*;

@RestController 
@RequestMapping("/api/v1/admin/support") 
@RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class SupportIntegrationController{

 private final TenantSettingsRepository repo; 
 private final ObjectMapper mapper;
 private final com.ricozknow.security.SecretEncryptionService secrets;

    @GetMapping 
    public Map<String,Object> get(){
        try{
            var s=repo.findById(TenantContext.get()).orElseThrow();
            JsonNode n=mapper.readTree(s.getSettingsJson());
            Map<String,Object> out=new HashMap<>();
            out.put("provider",n.path("helpdeskProvider").asText(""));
            out.put("zendeskSubdomain",n.path("zendeskSubdomain").asText(""));
            out.put("zendeskEmail",n.path("zendeskEmail").asText(""));
            out.put("freshdeskDomain",n.path("freshdeskDomain").asText(""));
            out.put("configured",!n.path("helpdeskProvider").asText("").isBlank());
            return out;
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
    }

    private String secretContext(String provider){return "helpdesk:"+TenantContext.get()+":"+provider;}
    @PutMapping 
    public Map<String,Object> save(@RequestBody Map<String,String> req){
        try{
            var s=repo.findById(TenantContext.get()).orElseThrow();
            ObjectNode n=(ObjectNode)mapper.readTree(s.getSettingsJson());
            String p=req.getOrDefault("provider","").toUpperCase();
            
            n.put("helpdeskProvider",p);
            
            if("ZENDESK".equals(p)){
                n.put("zendeskSubdomain",req.getOrDefault("subdomain",""));
                n.put("zendeskEmail",req.getOrDefault("email",""));
                String token=req.getOrDefault("apiToken","");
                if(!token.isBlank()) n.put("zendeskApiToken",secrets.encrypt(token,secretContext("ZENDESK")));
            }
            
            if("FRESHDESK".equals(p)){
                n.put("freshdeskDomain",req.getOrDefault("domain",""));
                String key=req.getOrDefault("apiKey","");
                if(!key.isBlank()) n.put("freshdeskApiKey",secrets.encrypt(key,secretContext("FRESHDESK")));
            }
            
            s.setSettingsJson(n.toString());
            s.setUpdatedAt(java.time.Instant.now());
            repo.save(s);return get();
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
}