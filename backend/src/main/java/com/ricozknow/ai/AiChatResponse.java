package com.ricozknow.ai;

/** Provider-neutral result from a chat/generation model. */
public record AiChatResponse(
        String text,
        String provider,
        String model,
        long inputTokens,
        long outputTokens
) {}
