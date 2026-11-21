package com.ricozknow.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Validates that a user-supplied URL (webhook registration, or any future
 * feature that makes the backend fetch/POST to an admin-provided address)
 * doesn't resolve to a private, loopback, or link-local address — the
 * standard SSRF mitigation for "the server will call this URL later"
 * features. Cloud metadata endpoints (169.254.169.254) are covered by the
 * link-local check.
 *
 * Deliberately re-run at delivery time as well as registration time (see
 * WebhookDeliveryWorker), not just once at creation: DNS can be changed by
 * whoever controls the target hostname after registration (DNS rebinding),
 * so a URL that resolved safely when registered isn't guaranteed to still
 * resolve safely when it's actually called.
 *
 * This is best-effort, not exhaustive: it does not protect against the
 * target server issuing an HTTP redirect to an internal address (this
 * class only validates the registered/stored URL, and the JDK HttpClient
 * follows redirects by default in some configurations) — worth revisiting
 * if webhook targets are ever allowed to be less trusted than "an admin
 * typed this in."
 *
 * enabled defaults true everywhere, including local dev, and should stay
 * true in any real deployment. The one legitimate reason to flip it off is
 * a test that deliberately delivers to a loopback address (its own embedded
 * HTTP server) to verify delivery mechanics — see WebhookDeliveryIT, which
 * sets ricozknow.security.webhooks.ssrf-guard-enabled=false explicitly and
 * only for itself, rather than this defaulting to off anywhere.
 */
@Component
public class SsrfGuard {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SsrfGuard.class);

    @Value("${ricozknow.security.webhooks.ssrf-guard-enabled:true}")
    private boolean enabled;

    public void assertSafeTarget(String url) {
        if (!enabled) {
            log.warn("SsrfGuard is disabled — allowing webhook target {} unchecked. "
                    + "This must never be true in a real deployment.", url);
            return;
        }

        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new SsrfViolationException("Not a valid URL: " + url);
        }

        String host = uri.getHost();
        if (host == null) {
            throw new SsrfViolationException("URL has no host: " + url);
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            throw new SsrfViolationException("Cannot resolve host: " + host);
        }

        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            throw new SsrfViolationException(
                    "URL resolves to a private/internal address, which is not allowed for webhook targets: " + host);
        }
    }

    public static class SsrfViolationException extends RuntimeException {
        public SsrfViolationException(String message) {
            super(message);
        }
    }
}
