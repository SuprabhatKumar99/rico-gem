package com.ricozknow.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Versioned authenticated encryption for database secrets. AAD binds ciphertext to its
 * tenant, owning object, and purpose; copying ciphertext to another record will fail.
 * The key ring is provided only by runtime configuration, never stored in the DB.
 *
 * Keys: RICOZ_SECRET_KEYS="k2026=<base64-of-32-random-bytes>,k2025=<...>"
 * Active key: RICOZ_SECRET_ACTIVE_KEY=k2026
 *
 * Unmarked legacy plaintext can be read temporarily for an explicit admin rotation.
 * New writes ALWAYS require a configured key. Do not remove old keys until rotation.
 */
@Component
public class SecretEncryptionService {
    private static final String PREFIX = "enc:v1:";
    private final Map<String, SecretKeySpec> keys;
    private final String activeKey;
    private final SecureRandom random = new SecureRandom();

    public SecretEncryptionService(
            @Value("${ricozknow.security.secrets.keys:}") String keyRing,
            @Value("${ricozknow.security.secrets.active-key:}") String activeKey) {
        this.activeKey = activeKey == null ? "" : activeKey.trim();
        Map<String, SecretKeySpec> parsed = new HashMap<>();
        if (keyRing != null && !keyRing.isBlank()) {
            for (String pair : keyRing.split(",")) {
                String[] entry = pair.trim().split("=", 2);
                if (entry.length != 2 || !entry[0].matches("[a-zA-Z0-9_-]{1,32}"))
                    throw new IllegalStateException("Invalid secret key ring entry");
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode(entry[1].trim()); }
                catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid secret encryption key", e); }
                if (bytes.length != 32 || parsed.putIfAbsent(entry[0], new SecretKeySpec(bytes, "AES")) != null)
                    throw new IllegalStateException("Secret keys must be unique AES-256 keys");
            }
        }
        if (!this.activeKey.isBlank() && !parsed.containsKey(this.activeKey))
            throw new IllegalStateException("Active secret encryption key is missing from key ring");
        this.keys = Map.copyOf(parsed);
    }

    public boolean encrypted(String value) { return value != null && value.startsWith("enc:"); }

    public String encrypt(String plaintext, String context) {
        if (plaintext == null) return null;
        SecretKeySpec key = keys.get(activeKey);
        if (key == null) throw new IllegalStateException("Configure RICOZ_SECRET_KEYS and RICOZ_SECRET_ACTIVE_KEY before storing authentication secrets");
        if (context == null || context.isBlank()) throw new IllegalArgumentException("Secret context is required");
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return PREFIX + activeKey + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot encrypt authentication secret", e);
        }
    }

    public String decrypt(String stored, String context) {
        if (stored == null || !encrypted(stored)) return stored; // legacy plaintext: rotate ASAP
        try {
            String[] parts = stored.split(":", 4);
            if (parts.length != 4 || !"enc".equals(parts[0]) || !"v1".equals(parts[1]))
                throw new IllegalStateException("Unsupported secret encryption version");
            SecretKeySpec key = keys.get(parts[2]);
            if (key == null) throw new IllegalStateException("Required secret decryption key is missing: " + parts[2]);
            byte[] combined = Base64.getDecoder().decode(parts[3]);
            if (combined.length < 29) throw new IllegalStateException("Invalid secret ciphertext");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, combined, 0, 12));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(combined, 12, combined.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Authentication secret could not be decrypted", e);
        }
    }

    /** Re-encrypts legacy/current/retired-key values with the current active key. */
    public String rotate(String stored, String context) {
        if (stored == null || stored.isBlank()) return stored;
        if (stored.startsWith(PREFIX + activeKey + ":") && !activeKey.isBlank()) return stored;
        return encrypt(decrypt(stored, context), context);
    }
}
