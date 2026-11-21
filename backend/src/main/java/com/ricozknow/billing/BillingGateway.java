package com.ricozknow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Isolates provider-specific transport and signature verification from billing rules. */
public interface BillingGateway {
    String customer(String tenantId, String name);
    Map<String,String> checkout(String customerId, String priceId, String tenantId);
    Map<String,String> portal(String customerId);
    void setCancelAtPeriodEnd(String subscriptionId, boolean cancel);
    JsonNode verifiedEvent(String payload, String signature);
}
