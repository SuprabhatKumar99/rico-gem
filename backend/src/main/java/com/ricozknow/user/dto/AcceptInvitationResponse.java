package com.ricozknow.user.dto;

import java.util.UUID;

public record AcceptInvitationResponse(UUID userId, UUID tenantId, String email, String message) {}
