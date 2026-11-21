package com.ricozknow.rag.feedback;
import java.time.Instant;
import java.util.UUID;
public record RagFeedbackResponse(UUID id, UUID conversationId, UUID messageId, boolean helpful, String comment, Instant createdAt) {}
