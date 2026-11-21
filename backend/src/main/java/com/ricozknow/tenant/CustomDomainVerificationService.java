package com.ricozknow.tenant;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.naming.directory.*;
import javax.naming.*;

@Service @RequiredArgsConstructor
public class CustomDomainVerificationService {
 private final TenantRepository repo;
 private final SecureRandom random=new SecureRandom();
 public record Challenge(String domain,String recordName,String recordValue,String instruction){}
 @Transactional
 public Challenge challenge(){
  Tenant t=repo.findById(TenantContext.get()).orElseThrow();
  String domain=t.getCustomDomain();if(domain==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Configure custom domain first");
  byte[] raw=new byte[32];random.nextBytes(raw);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  t.setCustomDomainChallengeHash(hash(t.getId()+":"+token));t.setCustomDomainChallengeCreatedAt(Instant.now());
  t.setCustomDomainVerified(false);t.setCustomDomainSslStatus("PENDING_DNS");repo.save(t);
  return new Challenge(domain,"_ricozknow-verification."+domain,"ricozknow-verification="+token,
   "Add the TXT record, then submit the token to verify domain ownership. This does not activate TLS.");
 }
 @Transactional
 public Tenant verify(String token){
  Tenant t=repo.findById(TenantContext.get()).orElseThrow();
  if(token==null||!token.matches("[A-Za-z0-9_-]{40,80}")||t.getCustomDomainChallengeHash()==null
   || t.getCustomDomainChallengeCreatedAt()==null||t.getCustomDomainChallengeCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(48))))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verification challenge expired or missing");
  byte[] supplied=hash(t.getId()+":"+token).getBytes(StandardCharsets.US_ASCII);
  byte[] expected=t.getCustomDomainChallengeHash().getBytes(StandardCharsets.US_ASCII);
  if(!MessageDigest.isEqual(supplied,expected))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid verification token");
  if(!txtMatches(t.getCustomDomain(),"ricozknow-verification="+token))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Verification TXT record not found");
  t.setCustomDomainVerified(true);t.setCustomDomainSslStatus("PENDING_EDGE_SSL");
  t.setCustomDomainChallengeHash(null);t.setCustomDomainChallengeCreatedAt(null);return repo.save(t);
 }
 /** An external edge controller must provision and attest TLS before an alias is routed. */
 static boolean txtMatches(String domain,String value){
  try{
   Hashtable<String,String> env=new Hashtable<>();env.put(Context.INITIAL_CONTEXT_FACTORY,"com.sun.jndi.dns.DnsContextFactory");
   DirContext dns=new InitialDirContext(env);
   try{Attributes attrs=dns.getAttributes("_ricozknow-verification."+domain,new String[]{"TXT"});
    Attribute txt=attrs.get("TXT");if(txt==null)return false;
    for(int i=0;i<txt.size();i++){
     String record=String.valueOf(txt.get(i)).replace("\\\" \\\"", "").replace("\"","").trim();
     if(record.equals(value))return true;
    }
    return false;
   }finally{dns.close();}
  }catch(Exception ignored){return false;}
 }
 private static String hash(String str){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(str.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
