package com.ricozknow.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.intelligence.dto.*;
import com.ricozknow.ai.AiChatResponse;
import com.ricozknow.ai.AiChatService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AiKnowledgeService {
    private final ObjectMapper mapper;
    private final MeterRegistry meterRegistry;
    private final AiChatService aiChatService;

    public AiGeneratedArticleResponse generate(AiGenerateRequest request) {
        String prompt = "Create a high-quality knowledge-base article. Return ONLY JSON with keys title, slug, outline, keywords, topics, content. content must be {blocks:[{type:'heading',level:1..4,text:'...'}|{type:'paragraph',text:'...'}|{type:'list',items:[...]}]}. Topic: " + request.topic() + ". Audience: " + nvl(request.audience(), "general") + ". Language: " + nvl(request.language(), "en") + ". Source material: " + nvl(request.sourceMaterial(), "none");
        JsonNode result = callJson(prompt, "article_generation");
        if (result == null || !result.isObject()) return fallbackGenerate(request);
        return new AiGeneratedArticleResponse(text(result,"title",request.topic()), slug(result.path("slug").asText(text(result,"title",request.topic()))), normalizeContent(result.path("content")), strings(result.path("keywords")), strings(result.path("topics")), strings(result.path("outline")));
    }

    public JsonNode improve(JsonNode content, String instruction) {
        String prompt = "Improve this knowledge-base article while preserving factual meaning. Return ONLY JSON object {blocks:[...]}. Use only supported blocks heading(level 1-4), paragraph, list(items), code. Instruction: " + nvl(instruction,"Improve clarity, structure, completeness and readability.") + ". Article: " + content.toString();
        JsonNode result = callJson(prompt, "article_improvement");
        return result == null ? content : normalizeContent(result);
    }

    public List<String> keywords(JsonNode content, String title) {
        JsonNode result = callJson("Extract 5-12 concise search keywords from this article. Return ONLY JSON array of strings. Title: " + title + ". Content: " + content.toString(), "metadata_keywords");
        return result != null && result.isArray() ? strings(result) : heuristicTerms(title + " " + flatten(content), 10);
    }

    public List<String> topics(JsonNode content, String title) {
        JsonNode result = callJson("Extract 3-8 broad topics from this article. Return ONLY JSON array of strings. Title: " + title + ". Content: " + content.toString(), "metadata_topics");
        return result != null && result.isArray() ? strings(result) : heuristicTerms(title + " " + flatten(content), 6);
    }

    public AiGeneratedArticleResponse suggestForGap(String query) {
        return generate(new AiGenerateRequest(query, "The user repeatedly searched for: " + query, null, "knowledge base users", "en"));
    }

    public String classifyFeedback(String queryOrArticle, String feedbackReason) {
        String prompt = "Classify negative knowledge-base feedback into exactly one label: INCORRECT, OUTDATED, INCOMPLETE, CONFUSING, WRONG_ARTICLE, MISSING_INFORMATION, OTHER. Return JSON {reason,confidence,rationale}. Feedback: " + nvl(feedbackReason,"No explicit reason") + ". Article/query context: " + queryOrArticle;
        JsonNode result = callJson(prompt, "feedback_classification");
        return result != null && result.has("reason") ? result.toString() : "{\"reason\":\"OTHER\",\"confidence\":0.20,\"rationale\":\"No model classification available\"}";
    }

    private JsonNode callJson(String prompt, String operation) {
        long start = System.nanoTime();
        try {
            AiChatResponse response = aiChatService.complete(
                    "You are a precise knowledge-management assistant. Never invent facts.",
                    prompt,
                    0.1,
                    true
            );
            long in = response.inputTokens(), out = response.outputTokens();
            meterRegistry.counter("ricozknow.ai.operation.input_tokens", "operation", operation).increment(in);
            meterRegistry.counter("ricozknow.ai.operation.output_tokens", "operation", operation).increment(out);
            meterRegistry.timer("ricozknow.ai.operation.latency", "operation", operation).record(System.nanoTime()-start, java.util.concurrent.TimeUnit.NANOSECONDS);
            return mapper.readTree(response.text());
        } catch (Exception ex) {
            meterRegistry.counter("ricozknow.ai.operation.errors", "operation", operation).increment();
            return null;
        }
    }
    private AiGeneratedArticleResponse fallbackGenerate(AiGenerateRequest r) {
        ObjectNode content=mapper.createObjectNode(); ArrayNode blocks=content.putArray("blocks");
        blocks.addObject().put("type","heading").put("level",1).put("text",r.topic());
        blocks.addObject().put("type","paragraph").put("text","This draft was generated from the requested topic and should be reviewed before publication.");
        blocks.addObject().put("type","heading").put("level",2).put("text","Overview");
        blocks.addObject().put("type","paragraph").put("text",nvl(r.sourceMaterial(),"Add verified source material here."));
        return new AiGeneratedArticleResponse(r.topic(),slug(r.topic()),content,heuristicTerms(r.topic(),8),heuristicTerms(r.topic(),5),List.of("Overview","Steps","Troubleshooting"));
    }
    private JsonNode normalizeContent(JsonNode n){ if(n.has("blocks")) return n; if(n.has("content")&&n.path("content").has("blocks")) return n.path("content"); return fallbackGenerate(new AiGenerateRequest("Draft",null,null,null,null)).content(); }
    private List<String> strings(JsonNode n){ if(n==null||!n.isArray()) return List.of(); List<String> out=new ArrayList<>(); n.forEach(x->{if(x.isTextual()&&!x.asText().isBlank()) out.add(x.asText().trim());}); return out.stream().distinct().limit(20).toList(); }
    private String text(JsonNode n,String k,String d){ return n.has(k)&&n.path(k).isTextual()?n.path(k).asText():d; }
    private String flatten(JsonNode n){ if(n==null)return ""; if(n.isTextual())return n.asText()+" "; StringBuilder b=new StringBuilder(); n.elements().forEachRemaining(x->b.append(flatten(x))); return b.toString(); }
    private List<String> heuristicTerms(String text,int limit){ return Arrays.stream(Normalizer.normalize(text.toLowerCase(Locale.ROOT),Normalizer.Form.NFKC).split("[^\\p{L}\\p{Nd}]+" )).filter(s->s.length()>3).filter(s->!Set.of("this","that","with","from","your","article","content","about","have","will","into","then","only","they","their","what","when","where","which").contains(s)).collect(Collectors.groupingBy(s->s,Collectors.counting())).entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(limit).map(Map.Entry::getKey).toList(); }
    private String slug(String s){ return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("^-|-$",""); }
    private String nvl(String a,String b){return a==null||a.isBlank()?b:a;}
}
