package com.ricozknow.ai;

/** Stable application-facing AI generation API. Business services depend on this, never on a vendor SDK/API. */
public interface AiChatService {
    AiChatResponse complete(String systemPrompt, String userPrompt, double temperature, boolean jsonMode);
}
