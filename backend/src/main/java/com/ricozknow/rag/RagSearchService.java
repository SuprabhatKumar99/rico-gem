package com.ricozknow.rag;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.RateLimiter;
import com.ricozknow.rag.dto.RagSearchRequest;
import com.ricozknow.rag.dto.RagSearchResponse;
import com.ricozknow.rag.usage.RagUsageLimitService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagSearchService {
    private final RagRetrievalService retrievalService;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final RagUsageLimitService ragUsageLimitService;
    @Value("${ricozknow.security.rate-limits.rag.max-requests:60}") private int maxRequests;
    @Value("${ricozknow.security.rate-limits.rag.window-seconds:60}") private long windowSeconds;
    @Value("${ricozknow.security.rag.enabled:false}") private boolean enabled;

    public RagSearchResponse search(RagSearchRequest request, RagApiPrincipal principal) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG vector search is disabled");
        if (!rateLimiter.allow("rag:" + principal.apiKeyId(), maxRequests, Duration.ofSeconds(windowSeconds))) {
            throw new com.ricozknow.common.TooManyRequestsException("RAG rate limit exceeded");
        }
        if (request.query() == null || request.query().trim().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank");
        ragUsageLimitService.consumeRagQuery();
        long start=System.nanoTime();
        RagSearchResponse result=retrievalService.retrieve(request);
        auditService.record("RAG_SEARCH", null, "RAG_SEARCH_EXECUTED", null, java.util.Map.of("query", request.query(), "returnedCount", result.results().size(), "latencyMs", (System.nanoTime()-start)/1_000_000));
        return result;
    }
}
