package com.ricozknow.enterprise.scim;

import com.ricozknow.audit.AuditService;
import com.ricozknow.common.TenantContext;
import com.ricozknow.enterprise.group.Group;
import com.ricozknow.enterprise.group.GroupRepository;
import com.ricozknow.user.User;
import com.ricozknow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** SCIM group resources mirror existing access-control groups, never auto-granting roles. */
@Service
@RequiredArgsConstructor
public class ScimGroupService {
    private final GroupRepository groups;
    private final UserRepository users;
    private final AuditService audit;

    public record GroupView(UUID id, String displayName, String externalId, List<UUID> members) {}
    public record Change(String op, String path, Object value) {}

    @Transactional(readOnly = true)
    public GroupView get(UUID id) { return view(owned(id)); }

    @Transactional(readOnly = true)
    public java.util.Optional<GroupView> findByDisplayName(String name) {
        if (name == null || name.isBlank()) return java.util.Optional.empty();
        return groups.findByTenantIdAndNameIgnoreCase(TenantContext.get(), name.trim()).map(this::view);
    }

    @Transactional(readOnly = true)
    public List<GroupView> list() {
        return groups.findByTenantIdOrderByNameAsc(TenantContext.get()).stream().map(this::view).toList();
    }

    @Transactional
    public GroupView create(String name, String externalId, List<UUID> memberIds) {
        name = validatedName(name);
        if (groups.findByTenantIdAndNameIgnoreCase(TenantContext.get(), name).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Group displayName already exists");
        Group group = new Group();
        group.setTenantId(TenantContext.get());
        group.setName(name);
        group.setExternalId(externalId);
        group.getMembers().addAll(resolveMembers(memberIds));
        group = groups.save(group);
        audit.record("Group", group.getId(), "SCIM_GROUP_CREATED", null, group.getName());
        return view(group);
    }

    @Transactional
    public GroupView replace(UUID id, String name, String externalId, List<UUID> memberIds) {
        Group group = owned(id);
        rename(group, name);
        group.setExternalId(externalId);
        group.getMembers().clear();
        group.getMembers().addAll(resolveMembers(memberIds));
        audit.record("Group", group.getId(), "SCIM_GROUP_REPLACED", null, group.getName());
        return view(group);
    }

    @Transactional
    public GroupView patch(UUID id, List<Change> changes) {
        Group group = owned(id);
        if (changes == null || changes.isEmpty()) bad("SCIM Operations required");
        for (Change change : changes) {
            String op = change.op() == null ? "" : change.op().toLowerCase(Locale.ROOT);
            String path = change.path() == null ? "" : change.path().trim();
            String lower = path.toLowerCase(Locale.ROOT);
            if (!List.of("add", "remove", "replace").contains(op)) bad("Invalid SCIM group operation");
            if (lower.equals("displayname")) {
                if (op.equals("remove")) bad("Group displayName cannot be removed");
                if (!(change.value() instanceof String name)) bad("displayName must be a string");
                rename(group, (String) change.value());
            } else if (lower.equals("members") || lower.isBlank()) {
                if (lower.isBlank() && !(change.value() instanceof List<?>))
                    bad("Group members array required");
                if (op.equals("remove") && change.value() == null && lower.equals("members")) {
                    group.getMembers().clear();
                    continue;
                }
                List<UUID> ids = ScimGroupController.memberIds(change.value());
                Set<User> membership = resolveMembers(ids);
                switch (op) {
                    case "add" -> group.getMembers().addAll(membership);
                    case "remove" -> group.getMembers().removeIf(user -> ids.contains(user.getId()));
                    case "replace" -> { group.getMembers().clear(); group.getMembers().addAll(membership); }
                    default -> bad("Invalid SCIM operation");
                }
            } else if (lower.startsWith("members[value eq \"") && lower.endsWith("\"]") && op.equals("remove")) {
                String idValue = path.substring("members[value eq \"".length(), path.length() - 2);
                UUID memberId = parseUuid(idValue);
                // A user from another tenant must never be enumerated or modified.
                User member = ownedUser(memberId);
                group.getMembers().removeIf(u -> u.getId().equals(member.getId()));
            } else {
                bad("Unsupported SCIM group path");
            }
        }
        audit.record("Group", group.getId(), "SCIM_GROUP_PATCHED", null, null);
        return view(group);
    }

    @Transactional
    public void delete(UUID id) {
        Group group = owned(id);
        groups.delete(group);
        audit.record("Group", id, "SCIM_GROUP_DELETED", null, null);
    }

    private Group owned(UUID id) {
        return groups.findByTenantIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SCIM group not found"));
    }

    private User ownedUser(UUID id) {
        return users.findById(id).filter(u -> TenantContext.get().equals(u.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid group member"));
    }

    private Set<User> resolveMembers(List<UUID> ids) {
        if (ids == null) return Set.of();
        if (ids.size() > 1000) bad("Too many group members");
        return ids.stream().map(this::ownedUser).collect(Collectors.toSet());
    }

    private void rename(Group group, String name) {
        String validated = validatedName(name);
        groups.findByTenantIdAndNameIgnoreCase(TenantContext.get(), validated).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), group.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Group displayName already exists");
        });
        group.setName(validated);
    }

    private String validatedName(String value) {
        if (value == null || value.isBlank() || value.length() > 150) bad("Invalid group displayName");
        return value.trim();
    }

    private GroupView view(Group group) {
        List<UUID> ids = group.getMembers().stream().map(User::getId).sorted().toList();
        return new GroupView(group.getId(), group.getName(), group.getExternalId(), ids);
    }

    static UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid member identifier"); }
    }

    private static void bad(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
