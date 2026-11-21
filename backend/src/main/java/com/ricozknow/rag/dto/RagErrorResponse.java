package com.ricozknow.rag.dto;

import java.time.Instant;

public record RagErrorResponse(
        String code,
        String message,
        boolean retryable,
        String requestId,
        Instant timestamp
) {}
