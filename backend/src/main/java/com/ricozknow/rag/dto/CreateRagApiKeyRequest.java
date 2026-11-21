package com.ricozknow.rag.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record CreateRagApiKeyRequest(
        @Size(min = 1, max = 150) String name,
        Instant expiresAt,
        Set<String> scopes
) {}
