package com.ricozknow.connector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** AES-GCM with tenant and provider bound authenticated data; reads legacy V23 ciphertext for upgrades. */
@Component
public class ConnectorTokenVault {
    private final SecureRandom random = new SecureRandom();
    @Value("${ricozknow.connectors.encryption-key:}") private String keyMaterial;
    public boolean configured() { try { return keyBytes().length == 32; } catch(RuntimeException ex) {return false;} }
    private byte[] keyBytes(){
        try { byte[] key=Base64.getDecoder().decode(keyMaterial);if(key.length!=32)throw new IllegalArgumentException();return key; }
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A 32-byte base64 connector vault key must be configured");}
    }
    private byte[] aad(UUID tenant,String provider){return (tenant+":"+provider).getBytes(StandardCharsets.UTF_8);}
    public String seal(UUID tenant,String provider,String raw){
        try {byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keyBytes(),"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(aad(tenant,provider));byte[] encrypted=cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[12+encrypted.length];System.arraycopy(iv,0,out,0,12);System.arraycopy(encrypted,0,out,12,encrypted.length);return "v2:"+Base64.getEncoder().encodeToString(out);}
        catch(Exception ex){throw new IllegalStateException("Connector credential encryption failed",ex);}
    }
    public String open(UUID tenant,String provider,String payload){
        try {boolean modern=payload.startsWith("v2:");byte[] bytes=Base64.getDecoder().decode(modern?payload.substring(3):payload);if(bytes.length<29)throw new IllegalArgumentException("Short payload");byte[] iv=Arrays.copyOfRange(bytes,0,12);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(keyBytes(),"AES"),new GCMParameterSpec(128,iv));if(modern)cipher.updateAAD(aad(tenant,provider));return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);}
        catch(Exception ex){throw new IllegalStateException("Connector credential cannot be decrypted; check vault key/tenant",ex);}
    }
}
