package com.ricozknow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(@NotBlank String token, @NotBlank @Size(min = 12, max = 100) String password, @NotBlank @Size(max = 255) String name) {}
