package com.ricozknow.rag;

import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.ricozknow.enterprise.scope.ApiScope;

@Service
@RequiredArgsConstructor
public class RagApiKeyService {

    public static final String RAG_SEARCH_SCOPE = "RAG_SEARCH";
    private static final String PREFIX = "rk_live_";
    private final RagApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreatedKey create(String name, Instant expiresAt) { return create(name, expiresAt, java.util.Set.of(RAG_SEARCH_SCOPE)); }

    @Transactional
    public CreatedKey create(String name, Instant expiresAt, java.util.Set<String> requestedScopes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("API key name is required");
        }

        UUID tenantId = TenantContext.get();
        String raw = generateKey();

        RagApiKey key = new RagApiKey();
        key.setId(UUID.randomUUID());
        key.setTenantId(tenantId);
        key.setName(name.trim());
        key.setKeyPrefix(raw.substring(0, Math.min(raw.length(), 16)));
        key.setKeyHash(hash(raw));
        java.util.Set<String> normalized = (requestedScopes == null || requestedScopes.isEmpty()) ? java.util.Set.of(RAG_SEARCH_SCOPE) : requestedScopes.stream().map(String::trim).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet());
        normalized.forEach(scope -> { try { ApiScope.valueOf(scope); } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unsupported API scope: " + scope); } });
        key.setScopes(String.join(",", normalized));
        key.setStatus(RagApiKey.Status.ACTIVE);
        key.setExpiresAt(expiresAt);
        repository.save(key);

        return new CreatedKey(key.getId(), key.getName(), raw, key.getKeyPrefix(), expiresAt);
    }

    @Transactional(readOnly = true)
    public List<RagApiKey> list() {
        return repository.findByTenantIdOrderByCreatedAtDesc(TenantContext.get());
    }

    @Transactional
    public void revoke(UUID id) {
        RagApiKey key = repository.findById(id)
                .filter(k -> TenantContext.get().equals(k.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));
        key.setStatus(RagApiKey.Status.REVOKED);
        repository.save(key);
    }

    @Transactional
    public RagApiKey authenticate(String rawKey) { return authenticate(rawKey, null); }

    @Transactional
    public RagApiKey authenticate(String rawKey, String ipAddress) {
        if (rawKey == null || rawKey.isBlank()) return null;
        RagApiKey key = repository.findByKeyHashAndStatus(hash(rawKey), RagApiKey.Status.ACTIVE).orElse(null);
        if (key == null) return null;
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(Instant.now())) return null;
        if (ipAddress != null && key.getIpAllowlist() != null && !key.getIpAllowlist().isBlank() && !isAllowedIp(ipAddress, key.getIpAllowlist())) return null;
        key.setLastUsedAt(Instant.now());
        repository.save(key);
        return key;
    }

    @Transactional
    public CreatedKey rotate(UUID id) {
        RagApiKey old = repository.findById(id).filter(k -> TenantContext.get().equals(k.getTenantId())).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        old.setStatus(RagApiKey.Status.REVOKED); repository.save(old);
        return create(old.getName(), old.getExpiresAt(), java.util.Arrays.stream(old.getScopes().split(",")).filter(x->!x.isBlank()).collect(java.util.stream.Collectors.toSet()));
    }
    @Transactional
    public void setIpAllowlist(UUID id, String allowlist) { RagApiKey k=repository.findById(id).filter(x->TenantContext.get().equals(x.getTenantId())).orElseThrow(); k.setIpAllowlist(allowlist==null?"":allowlist.trim()); repository.save(k); }
    /** Safe CIDR matching: never treat DNS names as IP-allowlist entries. */
    private boolean isAllowedIp(String ip, String list) {
        if (ip == null || ip.isBlank()) return false;
        // Reject hostnames rather than performing remote DNS lookups for an allowlist rule.
        if (!ip.matches("[0-9a-fA-F.:]+")) return false;
        try {
            byte[] address = java.net.InetAddress.getByName(ip).getAddress();
            for (String item : list.split(",")) {
                String rule = item.trim();
                if (rule.isEmpty()) continue;
                String[] parts = rule.split("/", -1);
                if (parts.length > 2 || !parts[0].matches("[0-9a-fA-F.:]+")) continue;
                byte[] network = java.net.InetAddress.getByName(parts[0]).getAddress();
                if (network.length != address.length) continue;
                int bits;
                try { bits = parts.length == 1 ? network.length * 8 : Integer.parseInt(parts[1]); }
                catch (NumberFormatException ex) { continue; }
                if (bits < 0 || bits > network.length * 8) continue;
                int fullBytes = bits / 8;
                int remainingBits = bits % 8;
                boolean matches = true;
                for (int i = 0; i < fullBytes; i++) if (network[i] != address[i]) { matches = false; break; }
                if (matches && remainingBits > 0) {
                    int mask = (0xff << (8 - remainingBits)) & 0xff;
                    matches = (network[fullBytes] & mask) == (address[fullBytes] & mask);
                }
                if (matches) return true;
            }
        } catch (Exception ignored) {
            // Fail closed on malformed IP addresses and networks.
        }
        return false;
    }

    private String generateKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    public static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CreatedKey(UUID id, String name, String key, String prefix, Instant expiresAt) {}
}
