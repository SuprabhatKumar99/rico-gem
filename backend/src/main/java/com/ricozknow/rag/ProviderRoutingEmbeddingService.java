package com.ricozknow.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProviderRoutingEmbeddingService implements EmbeddingService {

    private final List<ProviderEmbedding> providers;

    @Value("${ricozknow.ai.embedding.provider:openai-compatible}")
    private String configuredProvider;

    @Override
    public List<Float> embed(String text) {

        String providerId = configuredProvider == null
                ? "openai-compatible"
                : configuredProvider.trim().toLowerCase(Locale.ROOT);

        ProviderEmbedding provider = providers.stream()
                .filter(candidate ->
                        candidate.providerId()
                                .equalsIgnoreCase(providerId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Unsupported embedding provider '"
                                        + providerId
                                        + "'. Supported providers: "
                                        + providers.stream()
                                        .map(ProviderEmbedding::providerId)
                                        .sorted()
                                        .toList()
                        )
                );

        return provider.embed(text);
    }
}