package com.ricozknow.aiplatform;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.regex.Pattern;

/** Fail-closed guardrails for the new staff AI workflows; lexical checks are defense in depth. */
@Component
public class AiSafetyPolicy {
    private static final Pattern SECRET=Pattern.compile("(?i)(?:sk-[A-Za-z0-9_-]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?:api[_ -]?key|password|access[_ -]?token)\\s*[:=]\\s*[^\\s,;]{12,})");
    private static final Pattern INJECTION=Pattern.compile("(?i)(?:ignore (?:all |previous |prior )?instructions|system prompt is|developer message:|reveal (?:your |the )?(?:system prompt|secrets)|disable (?:the )?(?:safety|access controls))");
    public String validateInput(String input,int max) {
        if(input==null||input.isBlank()||input.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid input length");
        if(SECRET.matcher(input).find())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Potential secret detected; remove it before using AI");
        return input.trim();
    }
    public String untrusted(String text) {
        if(text==null)return "";
        String value=text.length()>12000?text.substring(0,12000):text;
        return INJECTION.matcher(value).replaceAll("[UNTRUSTED INSTRUCTION REDACTED]");
    }
    public boolean hasSecret(String output){return output!=null && SECRET.matcher(output).find();}
}
