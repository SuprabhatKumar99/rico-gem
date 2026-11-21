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

/** Native Ollama adapter; no API key is required for a local Ollama server. */
@Component
@RequiredArgsConstructor
public class OllamaChatProvider implements AiChatProvider {
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${ricozknow.ai.ollama.generation-url:http://localhost:11434/api/chat}") private String endpoint;
    @Value("${ricozknow.ai.generation.model:llama3.2}") private String model;

    @Override public String providerId() { return "ollama"; }

    @Override
    public AiChatResponse complete(String systemPrompt, String userPrompt, double temperature, boolean jsonMode) {
        long start = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("stream", false);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            if (jsonMode) payload.put("format", "json");
            payload.put("options", Map.of("temperature", temperature));
            String response = restClient.post().uri(endpoint).body(mapper.writeValueAsString(payload))
                    .header("Content-Type", "application/json").retrieve().body(String.class);
            JsonNode root = mapper.readTree(response);
            String text = root.path("message").path("content").asText("");
            if (text.isBlank()) throw new IllegalStateException("Ollama returned an empty response");
            long in = root.path("prompt_eval_count").asLong(0);
            long out = root.path("eval_count").asLong(0);
            meterRegistry.timer("ricozknow.ai.provider.latency", "provider", providerId()).record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
            return new AiChatResponse(text, providerId(), model, in, out);
        } catch (Exception ex) {
            meterRegistry.counter("ricozknow.ai.provider.errors", "provider", providerId()).increment();
            throw new IllegalStateException("AI provider request failed: ollama", ex);
        }
    }
}
