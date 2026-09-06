package com.undertow.reconciliation.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckinRequest(
        @NotBlank(message = "requestId must not be blank")
        String requestId
) {
}
