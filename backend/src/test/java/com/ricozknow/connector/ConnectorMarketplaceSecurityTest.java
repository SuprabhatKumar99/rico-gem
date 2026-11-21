package com.ricozknow.connector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectorMarketplaceSecurityTest {
    @Mock com.ricozknow.billing.BillingService billing;
    @Mock JdbcTemplate db;
    @Mock ConnectorCredentialService credentials;
    @Mock ConnectorRemoteClient remote;
    @Test void rejectsInvalidRemotePathsBeforeDatabaseMutation(){
        var service=new ConnectorMarketplaceService(billing,db,credentials,remote);
        assertThatThrownBy(()->service.select(UUID.randomUUID(),"github","../private","bad",60)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.select(UUID.randomUUID(),"github","repo","bad",1)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,credentials,remote);
    }
    @Test void invalidProviderRejectedBeforeExternalAccess(){var service=new ConnectorMarketplaceService(billing,db,credentials,remote);
        assertThatThrownBy(()->service.select(UUID.randomUUID(),"unknown","repo","bad",60)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(db,credentials,remote);
    }
    @Test void documentSearchAndSourceManagementAreAdminOnly()throws Exception{
        for(String method:new String[]{"search","sources","browse","runs","select","sync","reindex","disable","remove"}){
            boolean restricted=java.util.Arrays.stream(ConnectorMarketplaceController.class.getDeclaredMethods()).filter(m->m.getName().equals(method))
                .anyMatch(m->{PreAuthorize ann=m.getAnnotation(PreAuthorize.class);return ann!=null && ann.value().contains("ADMIN");});
            assertThat(restricted).as(method).isTrue();
        }
    }
    @Test void noWildcardOrUnsupportedProviderInCatalog(){
        assertThat(ConnectorCatalog.ordered()).hasSize(7);
        assertThatThrownBy(()->ConnectorCatalog.require("http://127.0.0.1")).isInstanceOf(ResponseStatusException.class);
    }
}
