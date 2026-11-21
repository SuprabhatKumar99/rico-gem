package com.ricozknow.agent;

import com.ricozknow.analytics.AnalyticsEventService;
import com.ricozknow.analytics.AnalyticsEventType;
import com.ricozknow.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Spec section 18 distinguishes estimated deflection (inferred: a view with
 * no observed contact) from confirmed deflection (an integrated support
 * system explicitly says so). Before this class, nothing in the system could
 * ever emit SUPPORT_CONTACT_STARTED or DEFLECTION_RECORDED — "confirmed
 * deflection" and "support contact count" were structurally zero no matter
 * what happened in reality. This is the integration point that makes them
 * real, for any support tool willing to call it (see
 * reference-integrations/agent-api-example's report-contact/report-deflection
 * commands).
 */
@Service
@RequiredArgsConstructor
public class AgentDeflectionService {

    private final AnalyticsEventService analyticsEventService;
    private final AuditService auditService;

    public void reportSupportContactStarted(UUID articleId, String ticketId) {
        String metadata = ticketId != null ? "{\"ticketId\":\"" + escape(ticketId) + "\"}" : null;
        analyticsEventService.record(AnalyticsEventType.SUPPORT_CONTACT_STARTED, null, null, articleId, null, metadata);
        auditService.record("Deflection", articleId, "SUPPORT_CONTACT_REPORTED", null, ticketId);
    }

    public void reportDeflectionConfirmed(UUID articleId, String ticketId, String note) {
        String metadata = "{\"ticketId\":\"" + escape(ticketId) + "\""
                + (note != null && !note.isBlank() ? ",\"note\":\"" + escape(note) + "\"" : "")
                + "}";
        analyticsEventService.record(AnalyticsEventType.DEFLECTION_RECORDED, null, null, articleId, null, metadata);
        auditService.record("Deflection", articleId, "DEFLECTION_CONFIRMED", null, ticketId);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\"", "").replace("\\", "");
    }
}
