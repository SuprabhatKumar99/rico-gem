package com.ricozknow.ai;

/** Adapter contract for any chat-capable LLM provider. */
public interface AiChatProvider {
    String providerId();
    AiChatResponse complete(String systemPrompt, String userPrompt, double temperature, boolean jsonMode);
}
