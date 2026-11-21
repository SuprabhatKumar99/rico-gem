package com.ricozknow.webhook.dto;

import com.ricozknow.webhook.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record CreateWebhookRequest(
        @NotBlank @Pattern(regexp = "^https://.*", message = "must be an https:// URL") String url,
        @NotEmpty Set<WebhookEventType> eventTypes
) {
}
