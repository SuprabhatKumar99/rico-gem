package com.ricozknow.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Seven-provider OAuth: server-persisted single-use state, PKCE and encrypted token vault. */
@RestController @RequestMapping("/api/v1/connectors/oauth") @RequiredArgsConstructor
public class OAuthConnectorController {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final ConnectorTokenVault vault;
    private final SecureRandom random=new SecureRandom();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    @Value("${ricozknow.connectors.callback-base:}") private String callbackBase;
    @Value("${ricozknow.connectors.frontend-url:}") private String frontendUrl;
    @Value("${ricozknow.connectors.google.client-id:}") private String googleId;
    @Value("${ricozknow.connectors.google.client-secret:}") private String googleSecret;
    @Value("${ricozknow.connectors.notion.client-id:}") private String notionId;
    @Value("${ricozknow.connectors.notion.client-secret:}") private String notionSecret;
    @Value("${ricozknow.connectors.confluence.client-id:}") private String confluenceId;
    @Value("${ricozknow.connectors.confluence.client-secret:}") private String confluenceSecret;
    @Value("${ricozknow.connectors.sharepoint.client-id:}") private String sharepointId;
    @Value("${ricozknow.connectors.sharepoint.client-secret:}") private String sharepointSecret;
    @Value("${ricozknow.connectors.slack.client-id:}") private String slackId;
    @Value("${ricozknow.connectors.slack.client-secret:}") private String slackSecret;
    @Value("${ricozknow.connectors.github.client-id:}") private String githubId;
    @Value("${ricozknow.connectors.github.client-secret:}") private String githubSecret;
    @Value("${ricozknow.connectors.jira.client-id:}") private String jiraId;
    @Value("${ricozknow.connectors.jira.client-secret:}") private String jiraSecret;

    public record Connection(String provider,boolean connected,boolean configured,Instant connectedAt,Instant expiresAt) {}
    String clientId(String p){return switch(p){case "google-drive"->googleId;case "notion"->notionId;case "confluence"->confluenceId;case "sharepoint"->sharepointId;case "slack"->slackId;case "github"->githubId;case "jira"->jiraId;default->throw new IllegalArgumentException("provider");};}
    String clientSecret(String p){return switch(p){case "google-drive"->googleSecret;case "notion"->notionSecret;case "confluence"->confluenceSecret;case "sharepoint"->sharepointSecret;case "slack"->slackSecret;case "github"->githubSecret;case "jira"->jiraSecret;default->throw new IllegalArgumentException("provider");};}
    String callback(String p){return callbackBase.replaceAll("/$", "")+"/api/v1/connectors/oauth/callback/"+p;}
    boolean configured(String p){return !clientId(p).isBlank()&&!clientSecret(p).isBlank()&&!callbackBase.isBlank()&&vault.configured();}
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private String nonce(){byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static String challenge(String verifier){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));}catch(Exception e){throw new IllegalStateException(e);}}

    @GetMapping("/connections") @PreAuthorize("hasRole('ADMIN')")
    public List<Connection> connections(){UUID tenant=TenantContext.get();List<Connection> out=new ArrayList<>();for(var spec:ConnectorCatalog.ordered()){
        List<Connection> rows=db.query("select connected_at,expires_at from oauth_connector_connection where tenant_id=? and provider=?",(rs,i)->new Connection(spec.id(),true,configured(spec.id()),rs.getTimestamp(1).toInstant(),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()),tenant,spec.id());
        out.add(rows.isEmpty()?new Connection(spec.id(),false,configured(spec.id()),null,null):rows.get(0));
    }return out;}

    @PostMapping("/{provider}/authorize") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> authorize(@PathVariable String provider){
        var spec=ConnectorCatalog.require(provider);if(!configured(provider))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"OAuth provider and vault credentials are not configured");
        String state=nonce();String verifier=spec.pkce()?nonce():null;
        db.update("delete from connector_oauth_state where expires_at<now()");
        db.update("insert into connector_oauth_state(state_hash,tenant_id,provider,verifier,expires_at) values (?,?,?,?,now()+interval '10 minutes')",sha256(state),TenantContext.get(),provider,verifier);
        String url=spec.authEndpoint()+"?response_type=code&client_id="+enc(clientId(provider))+"&redirect_uri="+enc(callback(provider))+"&state="+enc(state);
        if(!spec.scope().isBlank())url+="&scope="+enc(spec.scope());
        if(spec.pkce())url+="&code_challenge="+enc(challenge(verifier))+"&code_challenge_method=S256";
        url+=switch(provider){case "google-drive"->"&access_type=offline&prompt=consent";case "notion"->"&owner=user";case "confluence","jira"->"&audience=api.atlassian.com&prompt=consent";default->"";};
        return Map.of("url",url);
    }

    @GetMapping("/callback/{provider}")
    public void callback(@PathVariable String provider,@RequestParam(required=false) String code,
            @RequestParam String state,@RequestParam(required=false) String error,HttpServletResponse response) throws Exception {
        ConnectorCatalog.Spec spec=ConnectorCatalog.require(provider);
        // DELETE ... RETURNING atomically consumes a single-use state even under concurrent callbacks.
        var states=db.query("delete from connector_oauth_state where state_hash=? and provider=? and expires_at>now() returning tenant_id,verifier",
             (rs,i)->new String[]{rs.getString(1),rs.getString(2)},sha256(state),provider);
        if(states.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid or expired OAuth state");
        if(error!=null||code==null||code.isBlank()){redirect(response,"error");return;}
        UUID tenant=UUID.fromString(states.get(0)[0]);String verifier=states.get(0)[1];
        JsonNode token=exchange(spec,code,verifier);
        if(token==null || token.path("access_token").asText().isBlank()){redirect(response,"error");return;}
        saveToken(tenant,provider,token);
        redirect(response,"connected");
    }
    private JsonNode exchange(ConnectorCatalog.Spec spec,String code,String verifier) throws Exception {
        String p=spec.id();
        String body="grant_type=authorization_code&code="+enc(code)+"&redirect_uri="+enc(callback(p))+"&client_id="+enc(clientId(p))+"&client_secret="+enc(clientSecret(p));
        if(verifier!=null)body+="&code_verifier="+enc(verifier);
        HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(spec.tokenEndpoint())).timeout(Duration.ofSeconds(20)).header("Accept","application/json");
        if(p.equals("notion")){b.header("Authorization","Basic "+Base64.getEncoder().encodeToString((clientId(p)+":"+clientSecret(p)).getBytes(StandardCharsets.UTF_8))).header("Notion-Version","2022-06-28").header("Content-Type","application/json");
           body=mapper.writeValueAsString(Map.of("grant_type","authorization_code","code",code,"redirect_uri",callback(p)));
        } else b.header("Content-Type","application/x-www-form-urlencoded");
        HttpResponse<String> res=client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()<200||res.statusCode()>=300)return null;
        JsonNode json=mapper.readTree(res.body());if(json.has("ok")&&!json.path("ok").asBoolean())return null;
        // Slack's bot access token is nested only when OAuth token rotation is enabled.
        return json;
    }
    void saveToken(UUID tenant,String provider,JsonNode token){
        Instant expires=token.path("expires_in").asLong(0)>0?Instant.now().plusSeconds(token.path("expires_in").asLong()):null;
        db.update("insert into oauth_connector_connection(tenant_id,provider,encrypted_token,connected_at,expires_at,updated_at) values (?,?,?,now(),?,now()) on conflict(tenant_id,provider) do update set encrypted_token=excluded.encrypted_token,expires_at=excluded.expires_at,updated_at=now()",
           tenant,provider,vault.seal(tenant,provider,token.toString()),expires==null?null:java.sql.Timestamp.from(expires));
    }
    private void redirect(HttpServletResponse response,String status)throws Exception{
        if(frontendUrl.isBlank()){response.setContentType("text/plain");response.getWriter().write("OAuth result: "+status);return;}
        response.sendRedirect(frontendUrl.replaceAll("/$","")+"/connectors?oauth="+status);
    }
    @DeleteMapping("/{provider}") @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void disconnect(@PathVariable String provider){ConnectorCatalog.require(provider);UUID tenant=TenantContext.get();
        db.update("delete from connector_source where tenant_id=? and provider=?",tenant,provider);
        db.update("delete from oauth_connector_connection where tenant_id=? and provider=?",tenant,provider);
    }
}
