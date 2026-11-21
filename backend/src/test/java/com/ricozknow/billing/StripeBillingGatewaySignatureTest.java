package com.ricozknow.billing;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class StripeBillingGatewaySignatureTest {
    static String signature(String secret,long at,String body) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return "t="+at+",v1="+HexFormat.of().formatHex(mac.doFinal((at+"."+body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void acceptsOnlySignedFreshExactBody() throws Exception {
        long now=1_800_000_000L;
        String body="{\"id\":\"evt_test1\",\"type\":\"invoice.paid\"}";
        String header=signature("whsec_secret",now,body);
        assertTrue(StripeBillingGateway.verifySignature(body,header,"whsec_secret",now));
        assertFalse(StripeBillingGateway.verifySignature(body+" ",header,"whsec_secret",now));
        assertFalse(StripeBillingGateway.verifySignature(body,header,"whsec_other",now));
        assertFalse(StripeBillingGateway.verifySignature(body,header,"whsec_secret",now+301));
        assertFalse(StripeBillingGateway.verifySignature(body,header,"",now));
        assertFalse(StripeBillingGateway.verifySignature(body,"t=notvalid,v1=abc","whsec_secret",now));
    }
}
