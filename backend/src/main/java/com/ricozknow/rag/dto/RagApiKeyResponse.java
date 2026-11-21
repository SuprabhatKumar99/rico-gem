package com.ricozknow.rag.dto;

import com.ricozknow.rag.RagApiKey;

import java.time.Instant;
import java.util.UUID;

public record RagApiKeyResponse(
        UUID id, String name, String prefix, String status, Instant createdAt, Instant expiresAt, Instant lastUsedAt, String ipAllowlist, String scopes
) {
    public static RagApiKeyResponse from(RagApiKey key) {
        return new RagApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getStatus().name(),
                key.getCreatedAt(), key.getExpiresAt(), key.getLastUsedAt(), key.getIpAllowlist(), key.getScopes());
    }
}
