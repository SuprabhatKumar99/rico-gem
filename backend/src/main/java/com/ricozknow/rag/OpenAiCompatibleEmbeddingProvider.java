package com.ricozknow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import com.ricozknow.enterprise.metering.UsageMeteringService;

import java.util.ArrayList;
import java.util.List;

/** Adapter for OpenAI and OpenAI-compatible embedding APIs. */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleEmbeddingProvider implements ProviderEmbedding {
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UsageMeteringService usageMeteringService;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${ricozknow.ai.embedding.provider:openai-compatible}") private String configuredProvider;
    @Value("${ricozknow.ai.embedding.url:https://api.openai.com/v1/embeddings}") private String endpoint;
    @Value("${ricozknow.ai.embedding.api-key:}") private String apiKey;
    @Value("${ricozknow.ai.embedding.model:text-embedding-3-small}") private String model;
    @Value("${ricozknow.ai.embedding.dimension:1536}") private int dimension;
    @Value("${ricozknow.ai.embedding.cost-per-1k-tokens:0.00002}") private double costPer1kTokens;

    @Override public String providerId() { return "openai-compatible"; }

    @Override
    public List<Float> embed(String text) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("AI embedding API key is not configured for provider " + configuredProvider);
        long started = System.nanoTime();
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("model", model, "input", text));
            String response = restClient.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey).body(body).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(response);
            long tokens = root.path("usage").path("total_tokens").asLong(0);
            if (tokens > 0) {
                meterRegistry.counter("ricozknow.rag.embedding.tokens").increment(tokens);
                usageMeteringService.recordAiCost(configuredProvider, model, "embedding", 0, 0, tokens, Math.round((tokens / 1000.0) * costPer1kTokens * 1_000_000), null);
            }
            JsonNode values = root.path("data").path(0).path("embedding");
            if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("Embedding provider returned no vector");
            List<Float> vector = new ArrayList<>(values.size());
            values.forEach(v -> vector.add((float) v.asDouble()));
            return normalizeAndValidate(vector);
        } catch (Exception ex) {
            meterRegistry.counter("ricozknow.rag.embedding.errors", "provider", configuredProvider).increment();
            if (ex instanceof IllegalStateException ise && ise.getMessage() != null && ise.getMessage().startsWith("Embedding")) throw ise;
            throw new IllegalStateException("Unable to call embedding provider " + configuredProvider, ex);
        } finally {
            meterRegistry.timer("ricozknow.rag.embedding.latency", "provider", configuredProvider).record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private List<Float> normalizeAndValidate(List<Float> vector) {
        if (vector.size() != dimension) throw new IllegalStateException("Embedding dimension mismatch: configured=" + dimension + ", provider returned=" + vector.size() + ". Change RAG_EMBEDDING_DIMENSION only when the OpenSearch vector index is created/rebuilt for that dimension.");
        double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
        if (norm > 0) for (int i = 0; i < vector.size(); i++) vector.set(i, (float)(vector.get(i) / norm));
        return vector;
    }
}
