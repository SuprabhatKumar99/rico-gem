package com.ricozknow.billing;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BillingEntitlementTest {
    private BillingService.Subscription sub(String plan,String status,Instant trialEnd){
        return new BillingService.Subscription(UUID.randomUUID(),plan,status,null,null,null,trialEnd,false);
    }
    @Test void paidAndLegacyTiersRemainAuthorized(){
        assertEquals("LEGACY",BillingService.effectivePlan(sub("LEGACY","LEGACY",null)));
        assertEquals("BUSINESS",BillingService.effectivePlan(sub("BUSINESS","ACTIVE",null)));
    }
    @Test void expiredAndUnpaidPlansFallbackToFree(){
        assertEquals("FREE",BillingService.effectivePlan(sub("GROWTH","TRIALING",Instant.now().minusSeconds(600))));
        assertEquals("GROWTH",BillingService.effectivePlan(sub("GROWTH","TRIALING",Instant.now().plusSeconds(600))));
        assertEquals("FREE",BillingService.effectivePlan(sub("BUSINESS","PAST_DUE",null)));
        assertEquals("FREE",BillingService.effectivePlan(sub("BUSINESS","UNPAID",null)));
        assertEquals("FREE",BillingService.effectivePlan(sub("BUSINESS","CANCELED",null)));
    }
}
