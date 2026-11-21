package com.ricozknow.rag.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record RagConversationResponse(UUID id,String title,Instant createdAt,Instant updatedAt,List<Message> messages){
 public record Message(UUID id,String role,String content,Instant createdAt){}
}
