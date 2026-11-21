package com.ricozknow.connector;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Base64;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ConnectorTokenVaultTest {
    @Test void ciphertextIsRandomAndBoundToTenantAndProvider(){
        ConnectorTokenVault vault=new ConnectorTokenVault();
        ReflectionTestUtils.setField(vault,"keyMaterial",Base64.getEncoder().encodeToString(new byte[32]));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();String secret="{\"access_token\":\"secret-value\"}";
        String first=vault.seal(a,"github",secret),second=vault.seal(a,"github",secret);
        assertThat(first).startsWith("v2:").isNotEqualTo(second).doesNotContain("secret-value");
        assertThat(vault.open(a,"github",first)).isEqualTo(secret);
        assertThatThrownBy(()->vault.open(b,"github",first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->vault.open(a,"slack",first)).isInstanceOf(IllegalStateException.class);
    }
    @Test void refusesMissingEncryptionKey(){ConnectorTokenVault vault=new ConnectorTokenVault();
        ReflectionTestUtils.setField(vault,"keyMaterial","");
        assertThat(vault.configured()).isFalse();
        assertThatThrownBy(()->vault.seal(UUID.randomUUID(),"github","token")).isInstanceOf(RuntimeException.class);
    }
}
