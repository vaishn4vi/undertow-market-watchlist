package com.undertow.auth.dto;

public record MeResponse(
        String email,
        String displayName
) {
}
