package com.undertow.watchlist.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateWatchlistItemRequest(
        @NotNull(message = "position is required")
        @PositiveOrZero(message = "position must be zero or positive")
        Integer position
) {
}
