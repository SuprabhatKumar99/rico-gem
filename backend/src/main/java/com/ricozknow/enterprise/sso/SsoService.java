package com.ricozknow.enterprise.sso;

import com.ricozknow.common.TenantContext;
import com.ricozknow.security.SecretEncryptionService; 
import com.ricozknow.audit.AuditService; 
import lombok.RequiredArgsConstructor; 
import org.springframework.stereotype.Service; 
import org.springframework.transaction.annotation.Transactional; 
import java.util.*; 
import java.net.URLEncoder; 
import java.nio.charset.StandardCharsets; 
import org.springframework.web.client.RestClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode; 
import com.fasterxml.jackson.databind.ObjectMapper;


@Service 
@RequiredArgsConstructor 
public class SsoService {
    private final SsoConnectionRepository repo; 
    private final AuditService audit;
    private final SecretEncryptionService secrets; 
    private final ObjectMapper mapper; 
    private final RestClient http=RestClient.builder().build();
    private final SamlRelyingPartyRegistrationRepository samlRegistrations;


    /** Construct the code+PKCE flow using server-generated state and nonce. */
    public String oidcAuthorizationUrl(String redirectUri, OidcLoginStateService.Started transaction) {
        var c = activeOidc();
        String auth = c.getAuthorizationUrl();
        if (auth == null || auth.isBlank()) auth = c.getIssuerUrl().replaceAll("/$", "") + "/authorize";
        return auth + (auth.contains("?") ? "&" : "?")
                + "response_type=code&client_id=" + enc(c.getClientId())
                + "&redirect_uri=" + enc(redirectUri)
                + "&scope=" + enc("openid profile email")
                + "&state=" + enc(transaction.state())
                + "&nonce=" + enc(transaction.nonce())
                + "&code_challenge_method=S256&code_challenge=" + enc(transaction.codeChallenge());
    }

    /** Validate a signed id_token, all binding claims, and the verified UserInfo identity. */
    public Map<String, String> exchangeOidc(String code, OidcLoginStateService.Transaction transaction) {
        if (code == null || code.isBlank() || code.length() > 2048)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid authorization code");
        var c = activeOidc();
        String tokenUrl = c.getTokenUrl();
        if (tokenUrl == null || tokenUrl.isBlank()) tokenUrl = c.getIssuerUrl().replaceAll("/$", "") + "/token";
        String body = "grant_type=authorization_code&code=" + enc(code)
                + "&redirect_uri=" + enc(transaction.redirectUri())
                + "&client_id=" + enc(c.getClientId())
                + "&client_secret=" + enc(secrets.decrypt(c.getClientSecretEncrypted(), secretContext(c)))
                + "&code_verifier=" + enc(transaction.codeVerifier());
        try {
            String response = http.post().uri(tokenUrl)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(body).retrieve().body(String.class);
            JsonNode tokens = mapper.readTree(response);
            String idToken = tokens.path("id_token").asText("");
            String accessToken = tokens.path("access_token").asText("");
            if (idToken.isBlank() || accessToken.isBlank()) invalidIdentity();

            // Fetch the issuer's trusted JWKs and cryptographically verify signature,
            // expiry, not-before and issuer. Never decode JWT claims without validation.
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(c.getIssuerUrl()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(c.getIssuerUrl()));
            Jwt jwt = decoder.decode(idToken);
            validateIdToken(jwt, c.getClientId(), transaction.nonce());

            String userInfoUrl = c.getUserinfoUrl();
            if (userInfoUrl == null || userInfoUrl.isBlank())
                userInfoUrl = c.getIssuerUrl().replaceAll("/$", "") + "/userinfo";
            String payload = http.get().uri(userInfoUrl)
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(String.class);
            JsonNode userInfo = mapper.readTree(payload);
            String subject = userInfo.path("sub").asText("");
            if (!jwt.getSubject().equals(subject)) invalidIdentity();
            String email = userInfo.path("email").asText("");
            if (email.isBlank()) email = jwt.getClaimAsString("email");
            // Never assume a verified identity just because a username looks like email.
            boolean verified = userInfo.path("email_verified").asBoolean(false)
                    || Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            if (!verified || email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) invalidIdentity();
            String name = userInfo.path("name").asText(email);
            return Map.of("email", email, "name", name == null || name.isBlank() ? email : name);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            // Do not leak credentials, provider responses, or id_token in an HTTP error.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OIDC identity verification failed", e);
        }
    }

    static void validateIdToken(Jwt jwt, String clientId, String nonce) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()
                || !jwt.getAudience().contains(clientId)
                || !nonce.equals(jwt.getClaimAsString("nonce"))) invalidIdentity();
        // OIDC multi-audience tokens must have an authorized party that matches this client.
        if (jwt.getAudience().size() > 1 && !clientId.equals(jwt.getClaimAsString("azp"))) invalidIdentity();
    }

    private SsoConnection activeOidc() {
        SsoConnection connection = repo.findByTenantIdAndProvider(TenantContext.get(), SsoConnection.Provider.OIDC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OIDC is not configured"));
        if (!connection.isEnabled() || connection.getIssuerUrl() == null || connection.getIssuerUrl().isBlank()
                || connection.getClientId() == null || connection.getClientId().isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OIDC is disabled or incomplete");
        return connection;
    }

    private static void invalidIdentity() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OIDC identity verification failed");
    }

    public static String secretContext(SsoConnection c) {
        return "sso-oidc:" + c.getTenantId() + ":" + c.getProvider();
    }

    private String enc(String x){return URLEncoder.encode(x==null?"":x,StandardCharsets.UTF_8);}
    
    @Transactional 
    public SsoConnection save(SsoConnection input){
        var tid=TenantContext.get();
        
        var c=repo.findByTenantIdAndProvider(tid,input.getProvider()).orElseGet(SsoConnection::new);c.setTenantId(tid);
        c.setProvider(input.getProvider());
        c.setEnabled(input.isEnabled());
        c.setClientId(input.getClientId());
        // Omitted/blank/redacted secrets must never overwrite an existing credential.
        if (input.getClientSecretEncrypted() != null && !input.getClientSecretEncrypted().isBlank()) {
            // Never accept ciphertext supplied by API callers (prevents ciphertext swapping).
            if (secrets.encrypted(input.getClientSecretEncrypted()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide plaintext client secret, not encrypted data");
            c.setClientSecretEncrypted(secrets.encrypt(input.getClientSecretEncrypted(), secretContext(c)));
        }
        c.setIssuerUrl(input.getIssuerUrl());
        c.setAuthorizationUrl(input.getAuthorizationUrl());
        c.setTokenUrl(input.getTokenUrl());
        c.setUserinfoUrl(input.getUserinfoUrl());
        c.setMetadataUrl(input.getMetadataUrl());
        c.setEntityId(input.getEntityId());
        c.setSettingsJson(input.getSettingsJson()==null?"{}":input.getSettingsJson());
        c.setSamlIdpEntityId(input.getSamlIdpEntityId());
        c.setSamlIdpSsoUrl(input.getSamlIdpSsoUrl());
        c.setSamlIdpCertificate(input.getSamlIdpCertificate());
        c.setSamlSpEntityId(input.getSamlSpEntityId());
        c.setSamlWantAuthnRequestsSigned(input.isSamlWantAuthnRequestsSigned());
        repo.save(c); 
        
        if (c.getProvider()==SsoConnection.Provider.SAML) samlRegistrations.evictTenantId(c.getTenantId()); 
        
        audit.record("SsoConnection",c.getId(),"SSO_CONFIGURATION_UPDATED",null,c.getProvider());
        
        return c;
    }
    
    @Transactional(readOnly=true) 
    public List<SsoConnection> list(){
        return repo.findByTenantId(TenantContext.get());
    }
}
