package com.ricozknow.agent.dto;

import java.util.UUID;

/**
 * articleId is optional: a support contact may start with no article having
 * been shown at all (customer went straight to a human). ticketId is the
 * CRM's own case/ticket identifier, stored in the analytics event's metadata
 * purely for traceability — RicozKnow never looks it up anywhere.
 */
public record SupportContactRequest(UUID articleId, String ticketId) {
}
