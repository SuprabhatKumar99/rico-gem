package com.ricozknow.rag.feedback;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record RagFeedbackRequest(@NotNull UUID conversationId, UUID messageId, @NotNull Boolean helpful, String comment) {}
