package com.ricozknow.enterprise.mfa;

import com.ricozknow.common.TenantContext;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import com.ricozknow.auth.RefreshTokenService;
import com.ricozknow.security.SecretEncryptionService;
import com.ricozknow.audit.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class MfaService {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final UserRepository userRepository;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final RefreshTokenService refreshTokens;
    private final AuditService audit;
    private final SecretEncryptionService secrets;
    private final MfaAttemptLimiter attempts;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public Enrollment enroll(User user) {
        assertTenant(user);
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Disable existing MFA using a valid authenticator or recovery code first");
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String encoded = base32Encode(secret);
        user.setMfaSecret(secrets.encrypt(encoded, context(user)));
        user.setMfaEnabled(false);
        userRepository.save(user);
        return new Enrollment(encoded, "otpauth://totp/RicozKnow:" + user.getEmail() + "?secret=" + encoded + "&issuer=RicozKnow");
    }

    @Transactional
    public RecoveryCodes enable(User user, String code) {
        assertTenant(user);
        if (user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA already enabled");
        if (user.getMfaSecret() == null || !verifyOrConsume(user, code))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid MFA code");
        user.setMfaSecret(secrets.rotate(user.getMfaSecret(), context(user)));
        user.setMfaEnabled(true);
        userRepository.save(user);
        RecoveryCodes result = new RecoveryCodes(issueRecoveryCodes(user));
        audit.record("User", user.getId(), "MFA_ENABLED", null, null);
        return result;
    }

    private List<String> issueRecoveryCodes(User user) {
        recoveryCodes.deleteForUser(user.getTenantId(), user.getId());
        List<String> plaintext = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            String value = HexFormat.of().formatHex(bytes).toUpperCase();
            MfaRecoveryCode stored = new MfaRecoveryCode();
            stored.setTenantId(user.getTenantId());
            stored.setUserId(user.getId());
            stored.setCodeHash(hash(value));
            recoveryCodes.save(stored);
            plaintext.add(value);
        }
        return plaintext;
    }

    @Transactional
    public void disable(User user, String code) {
        assertTenant(user);
        if (!user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is not enabled");
        if (!verifyOrConsume(user, code)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid MFA code");
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        recoveryCodes.deleteForUser(user.getTenantId(), user.getId());
        refreshTokens.revokeAllForUser(user.getId());
        audit.record("User", user.getId(), "MFA_DISABLED", null, null);
    }

    /** Called only from the distinct-admin approval transaction. Never issues new codes. */
    @Transactional
    public void resetAfterDualAdminApproval(User target) {
        assertTenant(target);
        if (!target.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA already disabled");
        target.setMfaEnabled(false);
        target.setMfaSecret(null);
        userRepository.save(target);
        recoveryCodes.deleteForUser(target.getTenantId(), target.getId());
        refreshTokens.revokeAllForUser(target.getId());
        audit.record("User", target.getId(), "MFA_ADMINISTRATIVELY_RESET", null, null);
    }

    /** Rotate all recovery codes after verifying existing MFA. Old codes are invalidated atomically. */
    @Transactional
    public RecoveryCodes regenerate(User user, String proof) {
        assertTenant(user);
        if (!user.isMfaEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "MFA is not enabled");
        if (!verifyOrConsume(user, proof)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid MFA code");
        RecoveryCodes issued = new RecoveryCodes(issueRecoveryCodes(user));
        audit.record("User", user.getId(), "MFA_RECOVERY_CODES_ROTATED", null, null);
        return issued;
    }

    /** Accept a valid TOTP or atomically consume a single-use, high-entropy recovery code. */
    @Transactional
    public boolean verifyOrConsume(User user, String code) {
        assertTenant(user);
        if (attempts.isBlocked(user.getTenantId(), user.getId())) return false;
        String secret = secrets.decrypt(user.getMfaSecret(), context(user));
        boolean valid = verify(secret, code, Instant.now().getEpochSecond());
        if (!valid && code != null && code.matches("(?i)[0-9a-f]{32}")) {
            valid = recoveryCodes.consume(user.getTenantId(), user.getId(), hash(code.toUpperCase())) == 1;
        }
        if (valid) {
            attempts.recordSuccess(user.getTenantId(), user.getId());
            return true;
        }
        attempts.recordFailure(user.getTenantId(), user.getId());
        return false;
    }

    private static String hash(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record RecoveryCodes(List<String> recoveryCodes) {}

    public boolean verify(String secret, String code, long epochSeconds) {
        if (secret == null || code == null || !code.matches("\\d{6}")) return false;
        byte[] key;
        try { key = base32Decode(secret); } catch (IllegalArgumentException ex) { return false; }
        long counter = epochSeconds / 30;
        for (long offset = -1; offset <= 1; offset++) if (generate(key, counter + offset).equals(code)) return true;
        return false;
    }

    private String generate(byte[] key, long counter) {
        try {
            byte[] msg = new byte[8];
            for (int i = 7; i >= 0; i--) { msg[i] = (byte) counter; counter >>>= 8; }
            Mac mac = Mac.getInstance("HmacSHA1"); mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg); int o = hash[hash.length - 1] & 0x0f;
            int bin = ((hash[o] & 0x7f) << 24) | ((hash[o+1] & 0xff) << 16) | ((hash[o+2] & 0xff) << 8) | (hash[o+3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception ex) { throw new IllegalStateException("Unable to generate TOTP", ex); }
    }

    private String base32Encode(byte[] bytes) {
        StringBuilder out = new StringBuilder((bytes.length * 8 + 4) / 5); int buffer = 0, bits = 0;
        for (byte b : bytes) { buffer = (buffer << 8) | (b & 0xff); bits += 8; while (bits >= 5) { bits -= 5; out.append(BASE32.charAt((buffer >> bits) & 31)); } }
        if (bits > 0) out.append(BASE32.charAt((buffer << (5 - bits)) & 31)); return out.toString();
    }
    private byte[] base32Decode(String value) {
        String s=value.replace(" ","").replace("=","").toUpperCase(); int buffer=0,bits=0; byte[] tmp=new byte[s.length()*5/8+1]; int pos=0;
        for(char c:s.toCharArray()){int v=BASE32.indexOf(c);if(v<0)throw new IllegalArgumentException("Invalid Base32 secret");buffer=(buffer<<5)|v;bits+=5;if(bits>=8){bits-=8;tmp[pos++]=(byte)((buffer>>bits)&255);}}
        return Arrays.copyOf(tmp,pos);
    }
    public static String context(User user) { return "mfa:" + user.getTenantId() + ":" + user.getId(); }

    private void assertTenant(User user) { if (!TenantContext.get().equals(user.getTenantId())) throw new IllegalArgumentException("User does not belong to current tenant"); }
    public record Enrollment(String secret, String otpauthUri) {}
}
