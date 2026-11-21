package com.ricozknow.automation;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Advisory detection only: not a replacement for enterprise DLP or a publication-blocking policy. */
@Component
public class SensitiveContentScanner {
    private static final Pattern PRIVATE_KEY = Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern API_TOKEN = Pattern.compile("(?i)\\b(?:api[_ -]?key|access[_ -]?token|secret[_ -]?key)\\s*[:=]\\s*['\"]?[a-z0-9_\\-]{16,}");
    private static final Pattern CARD_CANDIDATE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    public record Finding(String code,String severity,String detail) {}
    public List<Finding> scan(String text) {
        if(text==null || text.isBlank()) return List.of();
        if(text.length()>200_000) throw new IllegalArgumentException("Content exceeds DLP scan limit");
        List<Finding> findings=new ArrayList<>();
        if(PRIVATE_KEY.matcher(text).find()) findings.add(new Finding("PRIVATE_KEY","HIGH","Potential private key detected"));
        if(API_TOKEN.matcher(text).find()) findings.add(new Finding("POSSIBLE_SECRET","HIGH","Potential credential detected"));
        var candidates=CARD_CANDIDATE.matcher(text);
        while(candidates.find()) {
            String digits=candidates.group().replaceAll("\\D","");
            if(digits.length()>=13 && digits.length()<=19 && luhn(digits)) {
                findings.add(new Finding("POSSIBLE_PAYMENT_CARD","HIGH","Potential payment card number detected"));
                break;
            }
        }
        return List.copyOf(findings);
    }
    static boolean luhn(String digits) {
        int sum=0;boolean doubleDigit=false;
        for(int i=digits.length()-1;i>=0;i--) {
            int digit=digits.charAt(i)-'0';if(doubleDigit){digit*=2;if(digit>9)digit-=9;}
            sum+=digit;doubleDigit=!doubleDigit;
        }
        return sum%10==0;
    }
}
