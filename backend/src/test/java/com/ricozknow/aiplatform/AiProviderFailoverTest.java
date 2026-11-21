package com.ricozknow.aiplatform;

import com.ricozknow.ai.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;

class AiProviderFailoverTest {
    @Test void tenantSelectedProviderDoesNotLeakToGlobalFallback(){
        AiChatProvider ollama=mock(AiChatProvider.class), openai=mock(AiChatProvider.class);
        AiGovernanceService governance=mock(AiGovernanceService.class);
        AiUsageService usage=mock(AiUsageService.class);
        when(governance.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(governance.provider()).thenReturn("ollama");
        when(ollama.providerId()).thenReturn("ollama");
        when(openai.providerId()).thenReturn("openai-compatible");
        when(ollama.complete(anyString(),anyString(),anyDouble(),anyBoolean())).thenThrow(new IllegalStateException("offline"));
        ProviderRoutingChatService routing=new ProviderRoutingChatService(List.of(ollama,openai),governance,usage);
        ReflectionTestUtils.setField(routing,"provider","openai-compatible");
        ReflectionTestUtils.setField(routing,"failoverProviders","openai-compatible");
        assertThrows(IllegalStateException.class,()->routing.complete("s","u",0,false));
        verify(openai,never()).complete(anyString(),anyString(),anyDouble(),anyBoolean());
    }
    @Test void explicitlyConfiguredFallbackWorksWhenTenantHasNoOverride(){
        AiChatProvider ollama=mock(AiChatProvider.class), openai=mock(AiChatProvider.class);
        AiGovernanceService governance=mock(AiGovernanceService.class);
        AiUsageService usage=mock(AiUsageService.class);
        when(governance.redact(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(governance.provider()).thenReturn(null);
        when(ollama.providerId()).thenReturn("ollama");
        when(openai.providerId()).thenReturn("openai-compatible");
        when(ollama.complete(anyString(),anyString(),anyDouble(),anyBoolean())).thenThrow(new IllegalStateException("offline"));
        when(openai.complete(anyString(),anyString(),anyDouble(),anyBoolean())).thenReturn(new AiChatResponse("ok","openai-compatible","test",1,1));
        ProviderRoutingChatService routing=new ProviderRoutingChatService(List.of(ollama,openai),governance,usage);
        ReflectionTestUtils.setField(routing,"provider","ollama");
        ReflectionTestUtils.setField(routing,"failoverProviders","openai-compatible");
        assertEquals("ok",routing.complete("s","u",0,false).text());
    }
}
