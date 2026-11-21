package com.ricozknow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/** Short-lived, single-use PKCE/OIDC transactions and browser handoff tickets. */
@Service
@RequiredArgsConstructor
public class SocialLoginStateService {
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration STATE_TTL = Duration.ofMinutes(5);
    private static final Duration TICKET_TTL = Duration.ofMinutes(3);

    public record Transaction(String tenantSlug, String provider, String nonce,
                              String verifier, UUID linkUserId) {}
    public record Started(String state, String nonce, String challenge) {}
    public record Ticket(UUID tenantId, UUID userId) {}

    public Started start(String slug, String provider, UUID linkUserId) {
        String state = token();
        String nonce = token();
        String verifier = token();
        try {
            String payload = json.writeValueAsString(new Transaction(slug, provider, nonce, verifier, linkUserId));
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("rk:social:state:" + state, payload, STATE_TTL)))
                throw new IllegalStateException("State collision");
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return new Started(state, nonce, challenge);
        } catch (Exception ex) { throw new IllegalStateException("Could not start social sign-in", ex); }
    }

    public Transaction consume(String provider, String state) {
        if (state == null || !state.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String payload = redis.opsForValue().getAndDelete("rk:social:state:" + state);
        if (payload == null) throw invalid();
        try {
            Transaction txn = json.readValue(payload, Transaction.class);
            if (!provider.equals(txn.provider())) throw invalid();
            return txn;
        } catch (ResponseStatusException ex) { throw ex; }
          catch (Exception ex) { throw invalid(); }
    }

    public String ticket(UUID tenantId, UUID userId) {
        String value = token();
        try {
            redis.opsForValue().set("rk:social:ticket:" + value,
                    json.writeValueAsString(new Ticket(tenantId, userId)), TICKET_TTL);
            return value;
        } catch (Exception ex) { throw new IllegalStateException("Could not complete social sign-in", ex); }
    }

    public Ticket readTicket(String ticket) { return read(ticket, false); }
    public Ticket consumeTicket(String ticket) { return read(ticket, true); }
    private Ticket read(String ticket, boolean consume) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String key = "rk:social:ticket:" + ticket;
        String payload = consume ? redis.opsForValue().getAndDelete(key) : redis.opsForValue().get(key);
        if (payload == null) throw invalid();
        try { return json.readValue(payload, Ticket.class); }
        catch (Exception ex) { throw invalid(); }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social sign-in expired or invalid");
    }
    private static String token() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
