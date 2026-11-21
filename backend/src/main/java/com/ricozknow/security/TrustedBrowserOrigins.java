package com.ricozknow.security;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exact browser-origin allowlist for requests which authenticate via cookies.
 * Unlike general CORS patterns, wildcards are intentionally NOT supported:
 * tenant-controlled sibling subdomains must not silently gain credentialed
 * access to the central authentication API.
 */
public final class TrustedBrowserOrigins {
    private final Set<String> origins;

    public TrustedBrowserOrigins(String configured) {
        this.origins = Arrays.stream(configured == null ? new String[0] : configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(TrustedBrowserOrigins::canonicalOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (origins.isEmpty()) throw new IllegalArgumentException("At least one trusted browser origin is required");
    }

    public boolean permits(String originHeader, String refererHeader) {
        // If Origin was sent, it is authoritative; do not fall back to Referer
        // on an explicit 'null', malformed or untrusted Origin header.
        if (originHeader != null) {
            try { return origins.contains(canonicalOrigin(originHeader)); }
            catch (IllegalArgumentException ex) { return false; }
        }
        if (refererHeader == null) return false; // fail closed, never trust an absent signal
        try {
            URI referer = URI.create(refererHeader);
            if (referer.getHost() == null || referer.getUserInfo() != null) return false;
            String refOrigin = referer.getScheme() + "://" + referer.getHost()
                    + (referer.getPort() < 0 ? "" : ":" + referer.getPort());
            return origins.contains(canonicalOrigin(refOrigin));
        } catch (IllegalArgumentException ex) { return false; }
    }

    /** Public for focused standalone verification and consistent diagnostics. */
    public static String canonicalOrigin(String input) {
        if (input == null) throw new IllegalArgumentException("Origin is missing");
        try {
            URI uri = URI.create(input.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid browser origin");
            }
            int port = uri.getPort();
            if (port == 80 && scheme.equalsIgnoreCase("http")) port = -1;
            if (port == 443 && scheme.equalsIgnoreCase("https")) port = -1;
            String bracketedHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
            return scheme.toLowerCase(java.util.Locale.ROOT) + "://"
                    + bracketedHost.toLowerCase(java.util.Locale.ROOT)
                    + (port == -1 ? "" : ":" + port);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid browser origin", ex);
        }
    }
}
