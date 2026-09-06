package com.undertow.auth.dto;

public record AuthResponse(
        String token,
        String email,
        String displayName
) {
}
