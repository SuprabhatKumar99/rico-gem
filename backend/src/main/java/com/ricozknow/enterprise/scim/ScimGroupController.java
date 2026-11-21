package com.ricozknow.enterprise.scim;

import lombok.RequiredArgsConstructor;
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

/** SCIM 2.0 Groups backed by the same tenant groups used for knowledge permissions. */
@RestController
@RequestMapping("/scim/v2/Groups")
@RequiredArgsConstructor
public class ScimGroupController {
    private static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";
    private static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private final ScimGroupService service;
    private static final java.util.regex.Pattern GROUP_FILTER = java.util.regex.Pattern.compile(
            "^\\s*displayName\\s+eq\\s+\"([^\"]{1,150})\"\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) { return view(service.get(id)); }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int startIndex,
                                    @RequestParam(defaultValue = "100") int count,
                                    @RequestParam(required = false) String filter) {
        if (startIndex < 1 || count < 0 || count > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SCIM pagination");
        List<ScimGroupService.GroupView> all;
        if (filter != null && !filter.isBlank()) {
            var matcher = GROUP_FILTER.matcher(filter);
            if (!matcher.matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported SCIM filter");
            all = service.findByDisplayName(matcher.group(1)).stream().toList();
        } else all = service.list();
        int start = Math.min(startIndex - 1, all.size());
        int end = Math.min(all.size(), start + count);
        var resources = all.subList(start, end).stream().map(ScimGroupController::view).toList();
        return Map.of("schemas", List.of(LIST_SCHEMA), "totalResults", all.size(),
                "startIndex", startIndex, "itemsPerPage", resources.size(), "Resources", resources);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        var g = service.create(string(payload.get("displayName")), string(payload.get("externalId")), memberIds(payload.get("members")));
        return ResponseEntity.created(URI.create("/scim/v2/Groups/" + g.id())).body(view(g));
    }

    @PutMapping("/{id}")
    public Map<String, Object> replace(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        return view(service.replace(id, string(payload.get("displayName")),
                string(payload.get("externalId")), memberIds(payload.get("members"))));
    }

    @PatchMapping("/{id}")
    public Map<String, Object> patch(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        if (!(payload.get("Operations") instanceof List<?> operations) || operations.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SCIM Operations required");
        List<ScimGroupService.Change> changes = new ArrayList<>();
        for (Object value : operations) {
            if (!(value instanceof Map<?, ?> op))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SCIM operation");
            String verb = string(op.get("op"));
            String path = string(op.get("path"));
            if (path == null && op.get("value") instanceof Map<?, ?> attributes) {
                for (var entry : attributes.entrySet())
                    changes.add(new ScimGroupService.Change(verb, String.valueOf(entry.getKey()), entry.getValue()));
            } else {
                changes.add(new ScimGroupService.Change(verb, path, op.get("value")));
            }
        }
        return view(service.patch(id, changes));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }

    static List<UUID> memberIds(Object members) {
        if (members == null) return List.of();
        if (!(members instanceof List<?> items))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "members must be an array");
        if (items.size() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many members");
        List<UUID> ids = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> member) || !(member.get("value") instanceof String value))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Each member needs a value");
            ids.add(ScimGroupService.parseUuid(value));
        }
        return ids;
    }

    private static String string(Object value) { return value instanceof String s ? s : null; }

    private static Map<String, Object> view(ScimGroupService.GroupView group) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("schemas", List.of(GROUP_SCHEMA));
        view.put("id", group.id().toString());
        view.put("displayName", group.displayName());
        if (group.externalId() != null) view.put("externalId", group.externalId());
        view.put("members", group.members().stream()
                .map(id -> Map.of("value", id.toString(), "type", "User")).toList());
        view.put("meta", Map.of("resourceType", "Group", "location", "/scim/v2/Groups/" + group.id()));
        return view;
    }
}
