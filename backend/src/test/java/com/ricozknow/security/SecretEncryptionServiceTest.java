package com.ricozknow.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class SecretEncryptionServiceTest {
    private static String key(byte value) {
        byte[] raw = new byte[32]; Arrays.fill(raw, value);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test void encryptDecryptUsesAuthenticatedContextAndFreshNonce() {
        var crypt = new SecretEncryptionService("new=" + key((byte) 41), "new");
        String a = crypt.encrypt("totp-value", "mfa:tenant-a:user-a");
        String b = crypt.encrypt("totp-value", "mfa:tenant-a:user-a");
        assertThat(a).startsWith("enc:v1:new:").isNotEqualTo(b).doesNotContain("totp-value");
        assertThat(crypt.decrypt(a, "mfa:tenant-a:user-a")).isEqualTo("totp-value");
        assertThatThrownBy(() -> crypt.decrypt(a, "mfa:tenant-b:user-a"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypt.decrypt(a.substring(0, a.length()-4) + "AAAA", "mfa:tenant-a:user-a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void rotationRetainsOldDecryptKeyAndReEncryptsWithNewOne() {
        var before = new SecretEncryptionService("old=" + key((byte) 10), "old");
        String old = before.encrypt("client-secret", "sso:tenant-1");
        var after = new SecretEncryptionService("old=" + key((byte) 10) + ",new=" + key((byte) 12), "new");
        String rotated = after.rotate(old, "sso:tenant-1");
        assertThat(rotated).startsWith("enc:v1:new:");
        assertThat(after.decrypt(rotated, "sso:tenant-1")).isEqualTo("client-secret");
        assertThat(after.rotate(rotated, "sso:tenant-1")).isEqualTo(rotated);
        assertThatThrownBy(() -> new SecretEncryptionService("new=" + key((byte) 12), "new")
                .decrypt(old, "sso:tenant-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test void newWritesFailWithoutKeysButLegacyCanBeMigrated() {
        var unconfigured = new SecretEncryptionService("", "");
        assertThatThrownBy(() -> unconfigured.encrypt("secret", "context"))
                .isInstanceOf(IllegalStateException.class);
        var configured = new SecretEncryptionService("active=" + key((byte) 1), "active");
        assertThat(configured.decrypt("legacy-secret", "context")).isEqualTo("legacy-secret");
        assertThat(configured.rotate("legacy-secret", "context")).startsWith("enc:v1:active:");
    }
}
