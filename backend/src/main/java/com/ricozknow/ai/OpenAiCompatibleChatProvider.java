package com.ricozknow.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for OpenAI and providers exposing the OpenAI-compatible /v1/chat/completions contract.
 * This includes many hosted gateways and local servers such as vLLM/LM Studio, depending on their configuration.
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatProvider implements AiChatProvider {
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${ricozknow.ai.generation.provider:openai-compatible}") private String configuredProvider;
    @Value("${ricozknow.ai.generation.url:https://api.openai.com/v1/chat/completions}") private String endpoint;
    @Value("${ricozknow.ai.generation.api-key:}") private String apiKey;
    @Value("${ricozknow.ai.generation.model:gpt-4o-mini}") private String model;

    @Override public String providerId() { return "openai-compatible"; }

    @Override
    public AiChatResponse complete(String systemPrompt, String userPrompt, double temperature, boolean jsonMode) {
        long start = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("temperature", temperature);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            if (jsonMode) payload.put("response_format", Map.of("type", "json_object"));
            String body = mapper.writeValueAsString(payload);
            String response = ProviderHttpSupport.post(restClient, endpoint, apiKey, body).retrieve().body(String.class);
            JsonNode root = mapper.readTree(response);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) throw new IllegalStateException("AI provider returned an empty response");
            long in = root.path("usage").path("prompt_tokens").asLong(0);
            long out = root.path("usage").path("completion_tokens").asLong(0);
            meterRegistry.timer("ricozknow.ai.provider.latency", "provider", providerId()).record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
            return new AiChatResponse(text, configuredProvider == null || configuredProvider.isBlank() ? providerId() : configuredProvider, model, in, out);
        } catch (Exception ex) {
            meterRegistry.counter("ricozknow.ai.provider.errors", "provider", providerId()).increment();
            throw new IllegalStateException("AI provider request failed: " + providerId(), ex);
        }
    }
}
