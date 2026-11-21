package com.ricozknow.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Sent by an external support system that can positively confirm an article resolved a case without human intervention. */
public record ConfirmDeflectionRequest(
        @NotNull UUID articleId,
        @NotBlank String ticketId,
        String note
) {
}
