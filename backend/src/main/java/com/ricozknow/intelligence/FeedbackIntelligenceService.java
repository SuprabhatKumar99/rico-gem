package com.ricozknow.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.analytics.*;
import com.ricozknow.common.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class FeedbackIntelligenceService {
    private final AnalyticsEventRepository events;
    private final ArticleFeedbackClassificationRepository classifications;
    private final AiKnowledgeService ai;
    private final ObjectMapper mapper;

    @Transactional
    public void classify(UUID eventId, UUID articleId, String reason, String articleContext){
        if(eventId==null || classifications.existsByAnalyticsEventId(eventId)) return;
        String raw=ai.classifyFeedback(articleContext,reason);
        String label="OTHER"; double confidence=.2; String rationale="No model classification available";
        try{JsonNode n=mapper.readTree(raw);label=n.path("reason").asText("OTHER");confidence=n.path("confidence").asDouble(.2);rationale=n.path("rationale").asText(rationale);}catch(Exception ignored){}
        ArticleFeedbackClassification c=new ArticleFeedbackClassification();c.setTenantId(TenantContext.get());c.setAnalyticsEventId(eventId);c.setArticleId(articleId);c.setReason(label);c.setConfidence(Math.max(0,Math.min(1,confidence)));c.setRationale(rationale);classifications.save(c);
    }
}
