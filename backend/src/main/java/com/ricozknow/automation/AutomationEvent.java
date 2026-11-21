package com.ricozknow.automation;
import java.util.UUID;
/** Internal trusted domain event: never expose arbitrary rule execution to anonymous callers. */
public record AutomationEvent(UUID tenantId, String type, UUID articleId, String eventKey) {}
