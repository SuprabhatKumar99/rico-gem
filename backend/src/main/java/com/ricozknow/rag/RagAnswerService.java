package com.ricozknow.rag;

import com.ricozknow.ai.AiChatResponse;
import com.ricozknow.ai.AiChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricozknow.common.TenantContext;
import com.ricozknow.rag.conversation.*;
import com.ricozknow.rag.dto.*;
import io.micrometer.core.instrument.MeterRegistry;
import com.ricozknow.enterprise.metering.UsageMeteringService;
import com.ricozknow.audit.AuditService;
import com.ricozknow.rag.usage.RagUsageLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class RagAnswerService {
 private final RagRetrievalService retrievalService;
 private final RagConversationRepository conversationRepository;
 private final RagMessageRepository messageRepository;
 private final ObjectMapper objectMapper;
 private final MeterRegistry meterRegistry;
 private final UsageMeteringService usageMeteringService;
 private final RagUsageLimitService ragUsageLimitService;
 private final AuditService auditService;
 private final AiChatService aiChatService;
 @Value("${ricozknow.ai.generation.input-cost-per-1k-tokens:0.00015}") private double inputCostPer1k;
 @Value("${ricozknow.ai.generation.output-cost-per-1k-tokens:0.00060}") private double outputCostPer1k;
 @Value("${ricozknow.ai.answer.min-score:0.18}") private double minScore;
 @Value("${ricozknow.ai.answer.max-context-chars:12000}") private int maxContextChars;

 public RagAnswerResponse answer(RagAnswerRequest request) {
  long start=System.nanoTime(); UUID tenant=TenantContext.get();
  ragUsageLimitService.consumeRagQuery();
  RagConversation conversation=resolveConversation(tenant, request.conversationId(), request.query());
  List<RagMessage> history=new ArrayList<>(messageRepository.findTop20ByTenantIdAndConversationIdOrderByCreatedAtDesc(tenant, conversation.getId()));
  Collections.reverse(history);
  String retrievalQuery=rewriteQuery(request.query(), history);
  RagSearchRequest search=new RagSearchRequest(retrievalQuery, request.topK()==null?8:request.topK(), request.categoryId(), request.visibilities(), request.articleIds(), request.language(), null);
  RagSearchResponse retrieved=retrievalService.retrieve(search);
  List<RagAnswerResponse.Citation> citations=retrieved.results().stream().map(r -> new RagAnswerResponse.Citation("citation-"+r.rank(),r.chunkId(),r.articleId(),r.versionId(),r.title(),r.slug(),r.rank(),r.score(),r.content(),r.sourceId(),r.sourceType(),r.sourceUrl(),r.language(),r.content())).toList();
  double confidence=citations.isEmpty()?0.0:citations.stream().mapToDouble(RagAnswerResponse.Citation::score).average().orElse(0);
  boolean grounded=!citations.isEmpty() && confidence>=minScore;
  String answer=grounded ? generate(request.query(), history, citations) : "I couldn't find enough reliable information in the knowledge base to answer that question.";
  saveMessage(tenant,conversation.getId(),"user",request.query());
  saveMessage(tenant,conversation.getId(),"assistant",answer);
  conversation.setUpdatedAt(Instant.now()); conversationRepository.save(conversation);
  long latency=(System.nanoTime()-start)/1_000_000;
  meterRegistry.timer("ricozknow.rag.answer.latency").record(latency,java.util.concurrent.TimeUnit.MILLISECONDS);
  meterRegistry.counter("ricozknow.rag.answer.queries").increment();
  if(!grounded) meterRegistry.counter("ricozknow.rag.answer.ungrounded").increment();
  auditService.record("RAG_QUERY", conversation.getId(), grounded ? "RAG_ANSWER_GENERATED" : "RAG_NO_GROUNDED_ANSWER", null, Map.of("query", request.query(), "grounded", grounded, "confidence", confidence, "citationCount", citations.size(), "latencyMs", latency));
  return new RagAnswerResponse(answer,conversation.getId(),citations,latency,grounded,confidence,"1.0");
 }
 private RagConversation resolveConversation(UUID tenant, UUID id, String query){
  if(id!=null) return conversationRepository.findByTenantIdAndIdAndOwnerKeyId(tenant,id,RagApiAccess.keyId()).orElseThrow(()->new IllegalArgumentException("Conversation not found"));
  RagConversation c=new RagConversation(); c.setId(UUID.randomUUID()); c.setTenantId(tenant); c.setOwnerKeyId(RagApiAccess.keyId()); c.setTitle(query.length()>180?query.substring(0,180):query); c.setCreatedAt(Instant.now()); c.setUpdatedAt(Instant.now()); return conversationRepository.save(c);
 }
 private void saveMessage(UUID tenant,UUID conversation,String role,String content){ RagMessage m=new RagMessage();m.setId(UUID.randomUUID());m.setTenantId(tenant);m.setConversationId(conversation);m.setRole(role);m.setContent(content);m.setCreatedAt(Instant.now());messageRepository.save(m); }
 private String rewriteQuery(String query,List<RagMessage> history){
  if(history.isEmpty()) return query;
  String previous=history.stream().filter(m->"user".equals(m.getRole())).map(RagMessage::getContent).reduce((a,b)->b).orElse("");
  if(previous.isBlank()) return query;
  return previous+"\nFollow-up question: "+query;
 }
 private String generate(String query,List<RagMessage> history,List<RagAnswerResponse.Citation> citations){
  String context=citations.stream().map(c->"["+c.rank()+"] "+c.title()+"\n"+c.content()).collect(Collectors.joining("\n\n"));
  if(context.length()>maxContextChars) context=context.substring(0,maxContextChars);
  try{
   long generationStart=System.nanoTime();
   AiChatResponse response=aiChatService.complete(
    "Answer only from the supplied knowledge base context. If the context is insufficient, say so. Cite sources inline as [1], [2], etc. Do not invent facts.",
    "Question: "+query+"\n\nContext:\n"+context,
    0.1,
    false
   );
   long promptTokens=response.inputTokens(), completionTokens=response.outputTokens();
   if(promptTokens>0) meterRegistry.counter("ricozknow.rag.generation.input_tokens").increment(promptTokens);
   long costMicros=Math.round(((promptTokens/1000.0)*inputCostPer1k+(completionTokens/1000.0)*outputCostPer1k)*1_000_000);
   ragUsageLimitService.enforceAiCostLimit(costMicros);
   usageMeteringService.recordAiCost(response.provider(),response.model(),"generation",promptTokens,completionTokens,0,costMicros,null);
   if(completionTokens>0) meterRegistry.counter("ricozknow.rag.generation.output_tokens").increment(completionTokens);
   if(promptTokens>0) meterRegistry.counter("ricozknow.rag.generation.cost.usd").increment((promptTokens/1000.0)*inputCostPer1k);
   if(completionTokens>0) meterRegistry.counter("ricozknow.rag.generation.cost.usd").increment((completionTokens/1000.0)*outputCostPer1k);
   meterRegistry.timer("ricozknow.rag.generation.latency").record(System.nanoTime()-generationStart,java.util.concurrent.TimeUnit.NANOSECONDS);
   if(response.text()==null||response.text().isBlank()) throw new IllegalStateException("Generation provider returned an empty answer");
   return response.text();
  }catch(RagApiException ex){ throw ex; }
  catch(Exception ex){ meterRegistry.counter("ricozknow.rag.answer.generation_errors").increment(); throw new RagApiException("GENERATION_PROVIDER_ERROR", "The answer generation service is temporarily unavailable.", org.springframework.http.HttpStatus.BAD_GATEWAY, true); }
 }

}
