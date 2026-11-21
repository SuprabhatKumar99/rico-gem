package com.ricozknow.automation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensitiveContentScannerTest {
    private final SensitiveContentScanner scanner=new SensitiveContentScanner();
    @Test void doesNotFlagOrdinaryContent(){assertTrue(scanner.scan("Welcome to the product documentation.").isEmpty());}
    @Test void flagsPrivateKeyHeader(){assertEquals("PRIVATE_KEY",scanner.scan("-----BEGIN PRIVATE KEY-----").get(0).code());}
    @Test void flagsCredentialPattern(){assertEquals("POSSIBLE_SECRET",scanner.scan("api_key=abcdefghijklmnop1234").get(0).code());}
    @Test void flagsLuhnValidCandidate(){assertEquals("POSSIBLE_PAYMENT_CARD",scanner.scan("4111 1111 1111 1111").get(0).code());}
    @Test void rejectsInvalidCardCandidate(){assertTrue(scanner.scan("4111 1111 1111 1112").isEmpty());}
    @Test void limitsInput(){assertThrows(IllegalArgumentException.class,()->scanner.scan("A".repeat(200001)));}
}
