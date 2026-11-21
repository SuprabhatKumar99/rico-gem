package com.ricozknow.user.dto;

import com.ricozknow.role.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotEmpty Set<RoleName> roles
) {}
