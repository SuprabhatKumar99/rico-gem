package com.ricozknow.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConnectorWebhookSecurityTest {
    @Test void webhookWithInvalidSignatureNeverEnqueues(){
        var svc=mock(ConnectorMarketplaceService.class);var db=mock(JdbcTemplate.class);
        var controller=new ConnectorMarketplaceController(svc,mock(OAuthConnectorController.class),db,new ObjectMapper());
        ReflectionTestUtils.setField(controller,"githubWebhookSecret","secret");
        var headers=new HttpHeaders();headers.set("X-Hub-Signature-256","sha256=bad");
        assertThatThrownBy(()->controller.webhook("github",UUID.randomUUID(),UUID.randomUUID(),"{}",headers))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(svc,db);
    }
}
