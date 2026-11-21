package com.ricozknow.enterprise.scim;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Minimal SCIM resource discovery for identity-provider setup. Requires bearer auth. */
@RestController
@RequestMapping("/scim/v2")
public class ScimDiscoveryController {
    private static final String CORE = "urn:ietf:params:scim:schemas:core:2.0:";
    private static final String LIST = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

    @GetMapping("/ResourceTypes")
    public Map<String, Object> resourceTypes() {
        return Map.of("schemas", List.of(LIST), "totalResults", 2, "startIndex", 1, "itemsPerPage", 2,
                "Resources", List.of(type("User", "/Users"), type("Group", "/Groups")));
    }

    @GetMapping("/Schemas")
    public Map<String, Object> schemas() {
        var user = Map.of("schemas", List.of(CORE + "Schema"), "id", CORE + "User", "name", "User",
                "description", "Tenant user SCIM identity",
                "attributes", List.of(attribute("userName", "string", true, true),
                        attribute("displayName", "string", false, false),
                        attribute("active", "boolean", false, false)));
        var group = Map.of("schemas", List.of(CORE + "Schema"), "id", CORE + "Group", "name", "Group",
                "description", "Tenant knowledge permission group",
                "attributes", List.of(attribute("displayName", "string", true, false),
                        attribute("members", "complex", false, false)));
        return Map.of("schemas", List.of(LIST), "totalResults", 2, "startIndex", 1,
                "itemsPerPage", 2, "Resources", List.of(user, group));
    }

    private static Map<String, Object> type(String name, String endpoint) {
        return Map.of("schemas", List.of(CORE + "ResourceType"), "id", name, "name", name,
                "endpoint", endpoint, "schema", CORE + name,
                "meta", Map.of("resourceType", "ResourceType"));
    }

    private static Map<String, Object> attribute(String name, String type, boolean required, boolean unique) {
        return Map.of("name", name, "type", type, "multiValued", "members".equals(name),
                "required", required, "mutability", "readWrite", "returned", "default",
                "uniqueness", unique ? "server" : "none");
    }
}
