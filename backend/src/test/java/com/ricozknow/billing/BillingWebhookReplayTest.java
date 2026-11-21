package com.ricozknow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;

class BillingWebhookReplayTest {
    @Test void duplicateProviderEventIsIgnoredBeforeAnyTenantMutation() throws Exception {
        JdbcTemplate db=mock(JdbcTemplate.class);
        BillingGateway gateway=mock(BillingGateway.class);
        BillingService service=new BillingService(db,gateway);
        var event=new ObjectMapper().readTree("{\"id\":\"evt_123abc\",\"type\":\"invoice.paid\",\"created\":1790000000,\"data\":{\"object\":{\"customer\":\"cus_one\"}}}");
        when(db.update(anyString(),eq("evt_123abc"),eq("invoice.paid"),eq(1790000000L))).thenReturn(0);
        service.acceptEvent(event);
        verify(db,times(1)).update(anyString(),eq("evt_123abc"),eq("invoice.paid"),eq(1790000000L));
        verifyNoMoreInteractions(db);
        verifyNoInteractions(gateway);
    }
}
