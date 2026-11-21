package com.ricozknow.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrustedBrowserOriginsTest {
    private final TrustedBrowserOrigins trusted = new TrustedBrowserOrigins(
            "http://wiki.ricozknow.com:4200,https://app.example.com");

    @Test void acceptsExactAllowlistedOrigin() {
        assertTrue(trusted.permits("http://wiki.ricozknow.com:4200", null));
        assertTrue(trusted.permits("https://app.example.com", null));
        assertTrue(trusted.permits("https://app.example.com:443", null));
        assertTrue(trusted.permits(null, "https://app.example.com/security/settings?tab=security"));
    }

    @Test void rejectsCrossSiteAndSiblingSubdomains() {
        assertFalse(trusted.permits("https://evil.example.com", null));
        assertFalse(trusted.permits("https://app.example.com.evil.org", null));
        assertFalse(trusted.permits("http://wiki.ricozknow.com:8080", null));
        assertFalse(trusted.permits("null", "https://app.example.com/legitimate"));
        assertFalse(trusted.permits(null, "https://app.example.com.evil.org/"));
    }

    @Test void rejectsMissingAndMalformedOrigins() {
        assertFalse(trusted.permits(null, null));
        assertFalse(trusted.permits("*", null));
        assertFalse(trusted.permits("https://app.example.com/path", null));
        assertFalse(trusted.permits("https://app.example.com@attacker.org", null));
        assertTrue(trusted.permits("https://app.example.com", "https://attacker.org/"));
        assertThrows(IllegalArgumentException.class, () -> new TrustedBrowserOrigins("*"));
    }
}
