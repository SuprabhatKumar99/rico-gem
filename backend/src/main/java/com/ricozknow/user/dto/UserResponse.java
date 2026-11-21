package com.ricozknow.user.dto;

import com.ricozknow.user.User;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
        UUID id,
        String email,
        String name,
        User.UserStatus status,
        boolean emailVerified,
        Set<String> roles
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getName(), user.getStatus(), user.isEmailVerified(),
                user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toSet()));
    }
}
