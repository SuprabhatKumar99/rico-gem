package com.ricozknow.ai;
import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper; import com.ricozknow.common.TenantContext; import com.ricozknow.enterprise.settings.TenantSettingsService; import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service; import java.util.regex.Pattern;
@Service @RequiredArgsConstructor
public class AiGovernanceService {
 private final TenantSettingsService settings; private final ObjectMapper mapper;
 private static final Pattern EMAIL=Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
 private static final Pattern PHONE=Pattern.compile("\\b(?:\\+?\\d[\\d .()\\-]{7,}\\d)\\b");
 private static final Pattern CARD=Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b");
 public String redact(String input){ if(input==null||!settings.getOrCreate().isPiiRedactionEnabled()) return input; String x=EMAIL.matcher(input).replaceAll("[REDACTED_EMAIL]");x=PHONE.matcher(x).replaceAll("[REDACTED_PHONE]");return CARD.matcher(x).replaceAll("[REDACTED_NUMBER]");}
 public String provider(){String p=settings.getOrCreate().getAiProvider();return p==null||p.isBlank()?null:p;}
 public String region(){return settings.getOrCreate().getAiRegion();}
}
