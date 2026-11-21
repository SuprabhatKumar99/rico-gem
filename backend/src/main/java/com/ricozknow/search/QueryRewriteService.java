package com.ricozknow.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.ai.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class QueryRewriteService {
    private final AiChatService ai;
    private final ObjectMapper mapper;

    public Rewrite rewrite(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isBlank()) return new Rewrite(q, List.of());
        String lower = q.toLowerCase(Locale.ROOT);
        boolean eligible = q.split("\\s+").length <= 3 || q.endsWith("?") ||
                lower.startsWith("how ") || lower.startsWith("what ") || lower.startsWith("why ") ||
                lower.startsWith("where ") || lower.startsWith("can ") || lower.startsWith("is ");
        if (!eligible) return new Rewrite(q, List.of());
        try {
            JsonNode n = mapper.readTree(ai.complete(
                    "You rewrite knowledge-base search queries. Never invent product facts.",
                    "Return ONLY JSON {query:string, alternatives:string[]}. Rewrite this vague question into one concise retrieval query using only words already implied by it. Query: " + q,
                    0.0, true).text());
            String rewritten = n.path("query").asText(q).trim();
            List<String> alts = new ArrayList<>();
            n.path("alternatives").forEach(x -> { if (x.isTextual() && !x.asText().isBlank()) alts.add(x.asText().trim()); });
            return new Rewrite(rewritten.isBlank() ? q : rewritten, alts.stream().distinct().limit(3).toList());
        } catch (Exception ignored) {
            return new Rewrite(q, List.of());
        }
    }
    public record Rewrite(String query, List<String> alternatives) {}
}
