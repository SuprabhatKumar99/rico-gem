package com.ricozknow.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ricozknow.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Prevent secrets from becoming long-lived audit evidence by default. */
@Component
@RequiredArgsConstructor
public class AuditStateSanitizer {
    private final ObjectMapper mapper;

    public String safeJson(Object value) {
        if (value == null) return null;
        try {
            // Avoid serializing the JPA entity graph, which can recurse and
            // accidentally persist hashes, MFA secrets or other private fields.
            if (value instanceof User user) {
                Map<String, Object> safe = new LinkedHashMap<>();
                safe.put("id", user.getId());
                safe.put("tenantId", user.getTenantId());
                safe.put("email", user.getEmail());
                safe.put("status", user.getStatus());
                return mapper.writeValueAsString(safe);
            }
            JsonNode tree = mapper.valueToTree(value);
            scrub(tree);
            return mapper.writeValueAsString(tree);
        } catch (Exception ex) {
            // Audit event metadata is still recorded even for unserializable state.
            return "\"[state omitted]\"";
        }
    }

    private void scrub(JsonNode node) {
        if (node instanceof ObjectNode object) {
            var fields = object.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String field = entry.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                if (isSensitive(field)) object.put(entry.getKey(), "[REDACTED]");
                else scrub(entry.getValue());
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode entry : array) scrub(entry);
        }
    }

    private boolean isSensitive(String field) {
        return field.contains("password") || field.contains("secret") || field.contains("token")
                || field.contains("credential") || field.contains("authorization")
                || field.contains("apikey") || field.contains("privatekey")
                || field.contains("cookie") || field.contains("recoverycode");
    }
}
