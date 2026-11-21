package com.ricozknow.user;

import com.ricozknow.user.dto.CreateUserRequest;
import com.ricozknow.user.dto.InviteUserRequest;
import com.ricozknow.user.dto.UpdateUserRequest;
import com.ricozknow.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> list() {
        return userService.list().stream().map(UserResponse::from).toList();
    }

    /** Legacy direct-create endpoint retained for API compatibility; invitations are preferred. */
    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(UserResponse.from(userService.create(request)));
    }

    @PostMapping("/invite")
    public ResponseEntity<Void> invite(@Valid @RequestBody InviteUserRequest request) {
        userService.invite(request);
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(UserResponse.from(userService.update(id, request)));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(@PathVariable UUID id) {
        userService.disable(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(@PathVariable UUID id) {
        userService.enable(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(@PathVariable UUID id) {
        userService.revokeSessions(id);
        return ResponseEntity.noContent().build();
    }
}
