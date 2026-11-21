package com.ricozknow.enterprise.scim;

import com.ricozknow.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SCIM 2.0 Users subset; authorization/tenant establishment is enforced by ScimAuthFilter. */
@RestController
@RequestMapping("/scim/v2")
@RequiredArgsConstructor
public class ScimController {
    private static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private final ScimService service;
    private static final java.util.regex.Pattern USER_FILTER = java.util.regex.Pattern.compile(
            "^\\s*userName\\s+eq\\s+\"([^\"]{1,254})\"\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    @PostMapping("/Users")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        User user = service.create(asString(body.get("userName")), asString(body.get("displayName")),
                active(body.get("active"), true));
        return ResponseEntity.created(URI.create("/scim/v2/Users/" + user.getId())).body(view(user));
    }

    @GetMapping("/Users/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return view(service.get(id));
    }

    @GetMapping("/Users")
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int startIndex,
                                    @RequestParam(defaultValue = "100") int count,
                                    @RequestParam(required = false) String filter) {
        if (filter != null && !filter.isBlank()) {
            if (startIndex < 1 || count < 0 || count > 100)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SCIM pagination");
            var matcher = USER_FILTER.matcher(filter);
            if (!matcher.matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported SCIM filter");
            var matches = service.findByUserName(matcher.group(1)).stream().map(ScimController::view).toList();
            int start = Math.min(startIndex - 1, matches.size());
            int end = Math.min(matches.size(), start + count);
            return Map.of("schemas", List.of(LIST_SCHEMA), "totalResults", matches.size(), "startIndex", startIndex,
                    "itemsPerPage", end - start, "Resources", matches.subList(start, end));
        }
        Page<User> result = service.list(startIndex, count);
        List<Map<String, Object>> items = count == 0 ? List.of() : result.getContent().stream()
                .map(ScimController::view).toList();
        return Map.of("schemas", List.of(LIST_SCHEMA), "totalResults", result.getTotalElements(),
                "startIndex", startIndex, "itemsPerPage", items.size(), "Resources", items);
    }

    @PutMapping("/Users/{id}")
    public Map<String, Object> replace(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return view(service.replace(id, asString(body.get("userName")),
                asString(body.get("displayName")), active(body.get("active"), true)));
    }

    @PatchMapping("/Users/{id}")
    public Map<String, Object> patch(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Object ops = body.get("Operations");
        if (!(ops instanceof List<?> operations) || operations.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCIM Operations required");
        List<ScimService.Change> changes = new ArrayList<>();
        for (Object entry : operations) {
            if (!(entry instanceof Map<?, ?> operation))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SCIM operation");
            String verb = asString(operation.get("op"));
            if (!"replace".equalsIgnoreCase(verb) && !"add".equalsIgnoreCase(verb))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported SCIM operation");
            Object path = operation.get("path");
            Object value = operation.get("value");
            if (path instanceof String s && !s.isBlank()) {
                changes.add(new ScimService.Change(s, value));
            } else if (value instanceof Map<?, ?> attributes) {
                // SCIM allows a PATCH value object without an explicit path.
                for (var attr : attributes.entrySet())
                    changes.add(new ScimService.Change(String.valueOf(attr.getKey()), attr.getValue()));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCIM patch path required");
            }
        }
        return view(service.patch(id, changes));
    }

    @DeleteMapping("/Users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.deactivate(id);
    }

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> config() {
        return Map.of("schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
                "patch", Map.of("supported", true), "bulk", Map.of("supported", false),
                "filter", Map.of("supported", true, "maxResults", 100), "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false), "etag", Map.of("supported", false));
    }

    private static Map<String, Object> view(User user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("schemas", List.of(USER_SCHEMA));
        dto.put("id", user.getId().toString());
        dto.put("userName", user.getEmail());
        dto.put("displayName", user.getName());
        dto.put("active", user.getStatus() == User.UserStatus.ACTIVE);
        dto.put("emails", List.of(Map.of("value", user.getEmail(), "primary", true)));
        dto.put("meta", Map.of("resourceType", "User", "location", "/scim/v2/Users/" + user.getId()));
        return dto;
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static boolean active(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "active must be a boolean");
    }
}
