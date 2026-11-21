package com.ricozknow.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricozknow.article.dto.UpdateDraftContentRequest;
import com.ricozknow.common.TenantContext;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Short-interval synchronized editing with optimistic content revisions and ephemeral presence.
 * Deployments with multiple backend replicas must use Redis for presence and event fan-out.
 */
@RestController
@RequestMapping("/api/v1/articles/{articleId}/collaboration/{versionId}")
@RequiredArgsConstructor
public class CollaborationController {
    private final CollaborationService collaboration;

    public record SaveRequest(@NotNull JsonNode content, @NotNull String baseRevision, String changeSummary) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public CollaborationService.Snapshot snapshot(@PathVariable UUID articleId, @PathVariable UUID versionId) {
        return collaboration.snapshot(articleId, versionId);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CollaborationService.Snapshot save(@PathVariable UUID articleId, @PathVariable UUID versionId,
                                               @RequestBody SaveRequest request) {
        return collaboration.save(articleId, versionId, request);
    }

    @PostMapping("/presence")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public List<CollaborationService.Presence> heartbeat(@PathVariable UUID articleId, @PathVariable UUID versionId,
                                                        Authentication auth) {
        return collaboration.heartbeat(articleId, versionId, auth.getName());
    }

    @DeleteMapping("/presence")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR','REVIEWER')")
    public void leave(@PathVariable UUID articleId, @PathVariable UUID versionId, Authentication auth) {
        collaboration.leave(articleId, versionId, auth.getName());
    }
}

@Service
@RequiredArgsConstructor
class CollaborationService {
    private final ArticleService articleService;
    private final ArticleVersionRepository versions;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Instant>> presence = new ConcurrentHashMap<>();
    public record Snapshot(JsonNode content, String revision, String status, Instant checkedAt) {}
    public record Presence(String username, Instant lastSeen) {}

    private ArticleVersion find(UUID articleId, UUID versionId) {
        articleService.get(articleId); // authorizes tenant ownership of the parent
        ArticleVersion version = versions.findByTenantIdAndId(TenantContext.get(), versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!version.getArticle().getId().equals(articleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return version;
    }
    private static String digest(JsonNode node) {
        try {
            byte[] data = MessageDigest.getInstance("SHA-256")
                    .digest(node.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(data);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private Snapshot value(ArticleVersion v) {
        return new Snapshot(v.getContent(), digest(v.getContent()), v.getStatus().name(), Instant.now());
    }
    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID articleId, UUID versionId) { return value(find(articleId, versionId)); }

    @Transactional
    public Snapshot save(UUID articleId, UUID versionId, CollaborationController.SaveRequest request) {
        articleService.get(articleId);
        ArticleVersion v = versions.lockForCollaboration(TenantContext.get(), versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!v.getArticle().getId().equals(articleId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (v.getStatus() != VersionStatus.DRAFT && v.getStatus() != VersionStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft or rejected versions can be edited");
        if (!digest(v.getContent()).equals(request.baseRevision()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft changed remotely. Reload and merge before saving.");
        articleService.updateDraftContent(articleId, versionId,
                new UpdateDraftContentRequest(request.content(), request.changeSummary()));
        return value(v);
    }
    private String key(UUID articleId, UUID versionId) {
        return TenantContext.get() + ":" + articleId + ":" + versionId;
    }
    @Transactional(readOnly = true)
    public List<Presence> heartbeat(UUID articleId, UUID versionId, String username) {
        find(articleId, versionId);
        String key = key(articleId, versionId);
        var users = presence.computeIfAbsent(key, unused -> new ConcurrentHashMap<>());
        Instant now = Instant.now();
        users.entrySet().removeIf(e -> e.getValue().isBefore(now.minusSeconds(45)));
        users.put(username, now);
        return users.entrySet().stream().map(e -> new Presence(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Presence::username)).toList();
    }
    public void leave(UUID articleId, UUID versionId, String username) {
        var users = presence.get(key(articleId, versionId));
        if (users != null) users.remove(username);
    }
}
