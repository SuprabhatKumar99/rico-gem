package com.ricozknow.rag.dto;

import java.time.Instant;
import java.util.UUID;

public record CreatedRagApiKeyResponse(UUID id, String name, String key, String prefix, Instant expiresAt) {}
