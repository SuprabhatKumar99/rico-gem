package com.ricozknow.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/api/v1/connectors") @RequiredArgsConstructor
public class ConnectorMarketplaceController {
    private final ConnectorMarketplaceService marketplace;
    private final OAuthConnectorController oauth;
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    @Value("${ricozknow.connectors.github.webhook-secret:}") private String githubWebhookSecret;
    @Value("${ricozknow.connectors.slack.signing-secret:}") private String slackSigningSecret;
    public record Provider(String id,String name,boolean configured,boolean webhookSupported) {}
    public record Selection(@NotBlank String remoteId,@NotBlank String name,Integer intervalMinutes) {}
    @GetMapping("/catalog") @PreAuthorize("hasRole('ADMIN')")
    public List<Provider> catalog(){return ConnectorCatalog.ordered().stream().map(s->new Provider(s.id(),s.name(),oauth.configured(s.id()),s.webhookSupported())).toList();}
    @GetMapping("/{provider}/browse") @PreAuthorize("hasRole('ADMIN')")
    public ConnectorRemoteClient.Page browse(@PathVariable String provider,@RequestParam(required=false) String cursor){return marketplace.browse(TenantContext.get(),provider,cursor);}
    @GetMapping("/sources") @PreAuthorize("hasRole('ADMIN')")
    public List<ConnectorMarketplaceService.Source> sources(){return marketplace.sources(TenantContext.get());}
    @PostMapping("/{provider}/sources") @PreAuthorize("hasRole('ADMIN')")
    public ConnectorMarketplaceService.Source select(@PathVariable String provider,@Valid @RequestBody Selection selection){return marketplace.select(TenantContext.get(),provider,selection.remoteId(),selection.name(),selection.intervalMinutes());}
    @PostMapping("/sources/{id}/sync") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.ACCEPTED)
    public void sync(@PathVariable UUID id){marketplace.enqueue(TenantContext.get(),id);}
    @PostMapping("/sources/{id}/reindex") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.ACCEPTED)
    public void reindex(@PathVariable UUID id){marketplace.enqueue(TenantContext.get(),id);}
    @PostMapping("/sources/{id}/disable") @PreAuthorize("hasRole('ADMIN')")
    public void disable(@PathVariable UUID id){marketplace.disable(TenantContext.get(),id);}
    @DeleteMapping("/sources/{id}") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id){marketplace.remove(TenantContext.get(),id);}
    @GetMapping("/sources/{id}/runs") @PreAuthorize("hasRole('ADMIN')")
    public List<ConnectorMarketplaceService.SyncRun> runs(@PathVariable UUID id){return marketplace.runs(TenantContext.get(),id);}
    @GetMapping("/documents/search") @PreAuthorize("hasRole('ADMIN')")
    public List<ConnectorMarketplaceService.SearchHit> search(@RequestParam String q){return marketplace.search(TenantContext.get(),q);}

    /** Only Slack and GitHub have verified webhook handlers. Others use scheduled polling. */
    @PostMapping("/webhooks/{provider}/{tenant}/{source}")
    public Map<String,String> webhook(@PathVariable String provider,@PathVariable UUID tenant,@PathVariable UUID source,
               @RequestBody String body,@RequestHeader HttpHeaders headers) throws Exception {
        if(!provider.equals("github")&&!provider.equals("slack"))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String lower=provider.equals("github")?"x-hub-signature-256":"x-slack-signature";
        String signature=headers.getFirst(lower)==null?"":headers.getFirst(lower);
        String signing=provider.equals("github")?githubWebhookSecret:slackSigningSecret;
        if(signing==null||signing.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Webhook signature verification not configured");
        String signedPayload=body;
        if(provider.equals("slack")){
            String timestamp=headers.getFirst("x-slack-request-timestamp")==null?"":headers.getFirst("x-slack-request-timestamp");long time;
            try{time=Long.parseLong(timestamp);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
            if(Math.abs(Instant.now().getEpochSecond()-time)>300)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Expired webhook");
            signedPayload="v0:"+timestamp+":"+body;
        }
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(signing.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String calculated=(provider.equals("slack")?"v0=":"sha256=")+HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
        if(!MessageDigest.isEqual(calculated.getBytes(StandardCharsets.US_ASCII),signature.getBytes(StandardCharsets.US_ASCII)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid webhook signature");
        // Signature verified before looking up tenant/source existence or accepting challenges.
        int exists=db.queryForObject("select count(*) from connector_source where id=? and tenant_id=? and provider=? and disabled=false",Integer.class,source,tenant,provider);
        if(exists==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String delivery=provider.equals("github")?headers.getFirst("x-github-delivery")==null?"":headers.getFirst("x-github-delivery"):ConnectorMarketplaceService.class.getSimpleName()+":"+UUID.nameUUIDFromBytes(signedPayload.getBytes(StandardCharsets.UTF_8));
        if(delivery.isBlank()||delivery.length()>150)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid delivery ID");
        int inserted=db.update("insert into connector_webhook_delivery(tenant_id,provider,delivery_id) values(?,?,?) on conflict do nothing",tenant,provider,delivery);
        if(inserted==0)return Map.of("status","duplicate");
        db.update("delete from connector_webhook_delivery where seen_at<now()-interval '14 days'");
        JsonNode payload=mapper.readTree(body);
        if(provider.equals("slack")&&payload.path("type").asText().equals("url_verification"))return Map.of("challenge",payload.path("challenge").asText());
        marketplace.enqueue(tenant,source);
        return Map.of("status","queued");
    }
}
