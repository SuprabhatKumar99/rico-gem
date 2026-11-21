package com.ricozknow.article.collaboration;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController @RequiredArgsConstructor
public class CollaborationGatewayController {
    private final CollaborationGateway gateway;

    @PostMapping("/api/v1/articles/{articleId}/collaboration/{versionId}/ticket")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CollaborationGateway.Ticket ticket(@PathVariable UUID articleId,@PathVariable UUID versionId) {
        return gateway.mint(articleId,versionId);
    }

    public record Redeem(String token,String room) {}
    @PostMapping("/api/v1/internal/collaboration/authorize")
    public CollaborationGateway.Authorization authorize(@RequestHeader("X-Collaboration-Service-Key") String secret,
            @RequestBody Redeem req) {
        gateway.checkServiceKey(secret);
        return gateway.authorize(req.token(),req.room());
    }
    public record RoomPermission(String room,UUID actorId) {}
    @PostMapping("/api/v1/internal/collaboration/allow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void allow(@RequestHeader("X-Collaboration-Service-Key") String secret,@RequestBody RoomPermission req){
        gateway.checkServiceKey(secret);
        gateway.allowRoom(req.room(),req.actorId());
    }
    @GetMapping("/api/v1/internal/collaboration/{tenant}/{article}/{version}")
    public CollaborationGateway.Loaded load(@RequestHeader("X-Collaboration-Service-Key") String secret,
            @PathVariable UUID tenant,@PathVariable UUID article,@PathVariable UUID version) {
        gateway.checkServiceKey(secret);
        return gateway.load(tenant,article,version);
    }
    @PutMapping("/api/v1/internal/collaboration/{tenant}/{article}/{version}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void store(@RequestHeader("X-Collaboration-Service-Key") String secret,
            @PathVariable UUID tenant,@PathVariable UUID article,@PathVariable UUID version,
            @RequestBody CollaborationGateway.SavePayload req) {
        gateway.checkServiceKey(secret);
        gateway.store(tenant,article,version,req);
    }
}
