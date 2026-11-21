package com.ricozknow.auth.dto;

/** Body is entirely optional — browsers omit it and rely on the httpOnly cookie instead. */
public record RefreshRequest(String refreshToken) {
}
