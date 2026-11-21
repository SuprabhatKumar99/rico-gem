package com.ricozknow.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import com.ricozknow.aiplatform.AiUsageService;

/** Selects the configured provider once per request; application code stays provider-neutral. */
@Service
@RequiredArgsConstructor
public class ProviderRoutingChatService implements AiChatService {
    private final List<AiChatProvider> providers;
    private final AiGovernanceService governance;
    private final AiUsageService usage;
    @Value("${ricozknow.ai.generation.failover-providers:}") private String failoverProviders;
    @Value("${ricozknow.ai.generation.provider:openai-compatible}") private String provider;

    @Override
    public AiChatResponse complete(String systemPrompt, String userPrompt, double temperature, boolean jsonMode) {
        String tenantProvider = governance.provider();
        String selected = tenantProvider != null ? tenantProvider.trim().toLowerCase() : (provider == null ? "openai-compatible" : provider.trim().toLowerCase());
        if (selected.equals("openai") || selected.equals("huggingface") || selected.equals("custom") || selected.equals("openai-compatible")) selected = "openai-compatible";
        systemPrompt = governance.redact(systemPrompt); userPrompt = governance.redact(userPrompt);
        usage.reserve();
        List<String> order = new ArrayList<>(); order.add(selected);
        // Tenant-selected providers must not silently fall back to a different vendor.
        if (tenantProvider == null && failoverProviders != null && !failoverProviders.isBlank())
            Arrays.stream(failoverProviders.split(",")).map(String::trim).map(String::toLowerCase)
                    .filter(s -> !s.isBlank() && !order.contains(s)).forEach(order::add);
        RuntimeException last = null;
        for (String candidate : order) {
            String alias = switch(candidate) {case "openai", "huggingface", "custom" -> "openai-compatible"; default -> candidate;};
            AiChatProvider adapter = providers.stream().filter(p -> p.providerId().equalsIgnoreCase(alias)).findFirst().orElse(null);
            if (adapter == null) continue;
            long started=System.nanoTime();
            AiChatResponse answer;
            try {
                answer=adapter.complete(systemPrompt,userPrompt,temperature,jsonMode);
            } catch(RuntimeException ex) {
                last=ex;
                usage.record(new AiChatResponse("",alias,"unknown",0,0),(System.nanoTime()-started)/1_000_000,"chat",false);
                continue;
            }
            // A database/metering failure must never cause a second vendor generation.
            usage.record(answer,(System.nanoTime()-started)/1_000_000,"chat",true);
            return answer;
        }
        throw new IllegalStateException("No configured AI provider was available",last);
    }
}
