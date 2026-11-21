package com.ricozknow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/** Native Ollama embeddings adapter for fully local RAG deployments. */
@Component
@RequiredArgsConstructor
public class OllamaEmbeddingProvider implements ProviderEmbedding {
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${ricozknow.ai.ollama.embedding-url:http://localhost:11434/api/embeddings}") private String endpoint;
    @Value("${ricozknow.ai.embedding.model:nomic-embed-text}") private String model;
    @Value("${ricozknow.ai.embedding.dimension:768}") private int dimension;

    @Override public String providerId() { return "ollama"; }

    @Override
    public List<Float> embed(String text) {
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("model", model, "prompt", text));
            String response = restClient.post().uri(endpoint).body(body).header("Content-Type", "application/json").retrieve().body(String.class);
            JsonNode values = mapper.readTree(response).path("embedding");
            if (!values.isArray() || values.isEmpty()) throw new IllegalStateException("Ollama returned no embedding");
            List<Float> vector = new ArrayList<>(values.size()); values.forEach(v -> vector.add((float)v.asDouble()));
            if (vector.size() != dimension) throw new IllegalStateException("Embedding dimension mismatch: configured=" + dimension + ", Ollama returned=" + vector.size());
            double norm = Math.sqrt(vector.stream().mapToDouble(v -> v * v).sum());
            if (norm > 0) for (int i=0;i<vector.size();i++) vector.set(i,(float)(vector.get(i)/norm));
            return vector;
        } catch (Exception ex) {
            meterRegistry.counter("ricozknow.rag.embedding.errors", "provider", providerId()).increment();
            throw ex instanceof IllegalStateException ise ? ise : new IllegalStateException("Unable to call Ollama embedding provider", ex);
        }
    }
}
