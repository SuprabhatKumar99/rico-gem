package com.ricozknow.rag;

import com.ricozknow.rag.conversation.RagConversationService;
import com.ricozknow.rag.dto.*;
import com.ricozknow.rag.feedback.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/rag") @RequiredArgsConstructor
public class RagController {
 private final RagSearchService ragSearchService;
 private final RagAnswerService ragAnswerService;
 private final RagConversationService conversationService;
 private final RagFeedbackService feedbackService;
 @PostMapping("/search") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagSearchResponse search(@Valid @RequestBody RagSearchRequest request, org.springframework.security.core.Authentication authentication){ return ragSearchService.search(request,(RagApiPrincipal)authentication.getPrincipal()); }
 @PostMapping("/answer") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagAnswerResponse answer(@Valid @RequestBody RagAnswerRequest request){ return ragAnswerService.answer(request); }
 @GetMapping("/conversations") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public List<RagConversationResponse> conversations(){ return conversationService.list(); }
 @PostMapping("/conversations") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagConversationResponse createConversation(@RequestBody(required=false) RagConversationCreateRequest request){ return conversationService.create(request==null?null:request.title()); }
 @GetMapping("/conversations/{conversationId}") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagConversationResponse conversation(@PathVariable UUID conversationId){ return conversationService.get(conversationId); }
 @PatchMapping("/conversations/{conversationId}") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagConversationResponse renameConversation(@PathVariable UUID conversationId,@Valid @RequestBody RagConversationRenameRequest request){ return conversationService.rename(conversationId,request.title()); }
 @DeleteMapping("/conversations/{conversationId}") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public void deleteConversation(@PathVariable UUID conversationId){ conversationService.delete(conversationId); }
 @PostMapping("/feedback") @PreAuthorize("hasAuthority('SCOPE_RAG_SEARCH')")
 public RagFeedbackResponse feedback(@Valid @RequestBody RagFeedbackRequest request){ return feedbackService.upsert(request); }
}
