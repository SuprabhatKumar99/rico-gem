package com.ricozknow.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ConnectorCredentialService {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final ConnectorTokenVault vault;
    private final OAuthConnectorController oauth;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private static String e(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    /** FOR UPDATE prevents concurrent refresh-token rotation from losing the new refresh token. */
    @Transactional
    public String accessToken(UUID tenant,String provider){
        ConnectorCatalog.Spec spec=ConnectorCatalog.require(provider);
        var rows=db.query("select encrypted_token,expires_at from oauth_connector_connection where tenant_id=? and provider=? for update",
          (rs,i)->new Object[]{rs.getString(1),rs.getTimestamp(2)==null?null:rs.getTimestamp(2).toInstant()},tenant,provider);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.CONFLICT,"Connect "+provider+" before browsing or syncing");
        Object[] row=rows.get(0);JsonNode token;
        try{token=mapper.readTree(vault.open(tenant,provider,(String)row[0]));}catch(Exception ex){throw new IllegalStateException("Cannot open provider token",ex);}
        Instant expires=(Instant)row[1];
        if(expires==null||expires.isAfter(Instant.now().plusSeconds(90)))return token.path("access_token").asText();
        String refresh=token.path("refresh_token").asText("");
        if(refresh.isBlank())throw new ResponseStatusException(HttpStatus.CONFLICT,"Provider session expired; reconnect this integration");
        try{
            String form="grant_type=refresh_token&refresh_token="+e(refresh)+"&client_id="+e(oauth.clientId(provider))+"&client_secret="+e(oauth.clientSecret(provider));
            HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(spec.tokenEndpoint())).timeout(Duration.ofSeconds(20)).header("Accept","application/json");
            if(provider.equals("notion")){
                builder.header("Content-Type","application/json").header("Notion-Version","2022-06-28").header("Authorization","Basic "+Base64.getEncoder().encodeToString((oauth.clientId(provider)+":"+oauth.clientSecret(provider)).getBytes(StandardCharsets.UTF_8)));
                form=mapper.writeValueAsString(java.util.Map.of("grant_type","refresh_token","refresh_token",refresh));
            }else builder.header("Content-Type","application/x-www-form-urlencoded");
            HttpResponse<String> result=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
            if(result.statusCode()<200||result.statusCode()>=300)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Provider refresh failed; reconnect integration");
            JsonNode renewed=mapper.readTree(result.body());if(renewed.path("access_token").asText("").isBlank()||renewed.has("ok")&&!renewed.path("ok").asBoolean())throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Provider refresh was rejected");
            ObjectNode merged=((ObjectNode)token).deepCopy();renewed.fields().forEachRemaining(field->merged.set(field.getKey(),field.getValue()));
            long ttl=renewed.path("expires_in").asLong(0);Instant next=ttl>0?Instant.now().plusSeconds(ttl):null;
            db.update("update oauth_connector_connection set encrypted_token=?, expires_at=?,updated_at=now() where tenant_id=? and provider=?",
                vault.seal(tenant,provider,merged.toString()),next==null?null:java.sql.Timestamp.from(next),tenant,provider);
            return merged.path("access_token").asText();
        }catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw new IllegalStateException("Could not refresh provider token",ex);}
    }
}
