package com.ricozknow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Stripe API via fixed-origin HTTPS (no client-supplied endpoints and no secret exposure). */
@Component
public class StripeBillingGateway implements BillingGateway {
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Value("${RICOZ_STRIPE_SECRET_KEY:}") private String apiKey;
    @Value("${RICOZ_STRIPE_WEBHOOK_SECRET:}") private String webhookSecret;
    @Value("${RICOZ_BILLING_RETURN_URL:http://localhost:4200/billing}") private String returnUrl;
    public StripeBillingGateway(ObjectMapper json){this.json=json;}

    private void configured(){if(apiKey==null||apiKey.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Billing provider is not configured");}
    private JsonNode post(String path, Map<String,String> form){return post(path,form,UUID.randomUUID().toString());}
    private JsonNode post(String path, Map<String,String> form, String idempotencyKey){
        configured();
        String body=form.entrySet().stream().map(e->encode(e.getKey())+"="+encode(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");
        try{
            HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/"+path))
                .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/x-www-form-urlencoded")
                .header("Idempotency-Key",idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment provider request failed (HTTP "+response.statusCode()+")");
            return json.readTree(response.body());
        }catch(ResponseStatusException ex){throw ex;}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment provider interrupted",ex);}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment provider unavailable",ex);}
    }
    private static String encode(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    @Override public String customer(String tenantId,String name){
        return post("customers",Map.of("name",name,"metadata[ricoz_tenant_id]",tenantId),"ricozknow-customer-"+tenantId).path("id").asText();
    }
    @Override public Map<String,String> checkout(String customer,String price,String tenant){
        if(!returnUrl.startsWith("https://")&&!returnUrl.startsWith("http://localhost:"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure a trusted HTTPS billing return URL");
        JsonNode response=post("checkout/sessions",Map.of("mode","subscription","customer",customer,
            "line_items[0][price]",price,"line_items[0][quantity]","1",
            "client_reference_id",tenant,"metadata[ricoz_tenant_id]",tenant,
            "success_url",returnUrl+"?result=success","cancel_url",returnUrl+"?result=canceled"));
        return Map.of("sessionId",response.path("id").asText(),"url",response.path("url").asText());
    }
    @Override public Map<String,String> portal(String customer){
        JsonNode response=post("billing_portal/sessions",Map.of("customer",customer,"return_url",returnUrl));
        return Map.of("url",response.path("url").asText());
    }
    @Override public void setCancelAtPeriodEnd(String subscriptionId,boolean cancel){
        if(!subscriptionId.matches("sub_[a-zA-Z0-9]+"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid subscription");
        post("subscriptions/"+subscriptionId,Map.of("cancel_at_period_end",String.valueOf(cancel)));
    }
    @Override public JsonNode verifiedEvent(String payload,String signature){
        if(!verifySignature(payload,signature,webhookSecret,Instant.now().getEpochSecond()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Stripe webhook signature");
        try{return json.readTree(payload);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook JSON");}
    }
    /** Stripe t/v1 format; rejects replay outside five minutes and compares every provided v1 in constant time. */
    public static boolean verifySignature(String payload,String header,String secret,long nowEpoch){
        if(secret==null||secret.isBlank()||header==null||payload==null)return false;
        String timestamp=null;List<String> signatures=new ArrayList<>();
        for(String item:header.split(",")){
            String[] parts=item.trim().split("=",2);
            if(parts.length==2){if(parts[0].equals("t")) timestamp=parts[1];if(parts[0].equals("v1"))signatures.add(parts[1]);}
        }
        if(timestamp==null||signatures.isEmpty())return false;
        try{
            long t=Long.parseLong(timestamp);if(Math.abs(nowEpoch-t)>300)return false;
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] computed=mac.doFinal((timestamp+"."+payload).getBytes(StandardCharsets.UTF_8));
            for(String candidate:signatures){
                if(candidate.length()!=computed.length*2)continue;
                try {
                    byte[] submitted=HexFormat.of().parseHex(candidate);
                    if(MessageDigest.isEqual(computed,submitted))return true;
                } catch(IllegalArgumentException ignored) { /* continue checking other v1 signatures */ }
            }
        }catch(Exception ex){return false;}
        return false;
    }
}
