package com.ricozknow.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class HuggingFaceEmbeddingProvider implements ProviderEmbedding {

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    private final RestClient restClient =
            RestClient.builder().build();

    @Value("${ricozknow.ai.embedding.url}")
    private String baseUrl;

    @Value("${ricozknow.ai.embedding.api-key:}")
    private String apiKey;

    @Value("${ricozknow.ai.embedding.model}")
    private String model;

    @Value("${ricozknow.ai.embedding.dimension:768}")
    private int dimension;

    @Override
    public String providerId() {
        return "huggingface";
    }

    @Override
    public List<Float> embed(String text) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Hugging Face embedding API key is not configured"
            );
        }

        long started = System.nanoTime();

        try {

            String endpoint = buildEndpoint();

            String requestBody =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "inputs", text,
                                    "normalize", true
                            )
                    );

            String response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(
                            "Authorization",
                            "Bearer " + apiKey
                    )
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root =
                    objectMapper.readTree(response);

            List<Float> vector =
                    extractVector(root);

            return validateAndNormalize(vector);

        } catch (Exception ex) {

            meterRegistry
                    .counter(
                            "ricozknow.rag.embedding.errors",
                            "provider",
                            "huggingface"
                    )
                    .increment();

            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }

            throw new IllegalStateException(
                    "Unable to call Hugging Face embedding provider"
                            + " model=" + model
                            + ", endpoint=" + buildEndpoint(),
                    ex
            );

        } finally {

            meterRegistry
                    .timer(
                            "ricozknow.rag.embedding.latency",
                            "provider",
                            "huggingface"
                    )
                    .record(
                            System.nanoTime() - started,
                            TimeUnit.NANOSECONDS
                    );
        }
    }

    private String buildEndpoint() {

        String url = baseUrl.trim();

        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        /*
         * Hugging Face router feature-extraction endpoint:
         *
         * https://router.huggingface.co/hf-inference/models/{model}
         */
        return url
                + "/hf-inference/models/"
                + model;
    }

    private List<Float> extractVector(JsonNode root) {

        /*
         * Typical response:
         *
         * [
         *   [0.123, 0.456, ...]
         * ]
         */

        JsonNode values = root;

        if (root.isArray()
                && !root.isEmpty()
                && root.get(0).isArray()) {

            values = root.get(0);
        }

        if (!values.isArray()) {
            throw new IllegalStateException(
                    "Unexpected Hugging Face embedding response: "
                            + root
            );
        }

        List<Float> vector =
                new ArrayList<>(values.size());

        values.forEach(value ->
                vector.add((float) value.asDouble())
        );

        return vector;
    }

    private List<Float> validateAndNormalize(
            List<Float> vector) {

        if (vector.size() != dimension) {

            throw new IllegalStateException(
                    "Embedding dimension mismatch: configured="
                            + dimension
                            + ", provider returned="
                            + vector.size()
            );
        }

        double norm = Math.sqrt(
                vector.stream()
                        .mapToDouble(v -> v * v)
                        .sum()
        );

        if (norm > 0) {

            for (int i = 0; i < vector.size(); i++) {

                vector.set(
                        i,
                        (float)
                                (vector.get(i) / norm)
                );
            }
        }

        return vector;
    }
}