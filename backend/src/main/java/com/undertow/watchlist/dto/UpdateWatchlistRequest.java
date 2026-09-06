package com.undertow.watchlist.dto;

import jakarta.validation.constraints.Size;

public record UpdateWatchlistRequest(
        @Size(max = 100, message = "name must be 100 characters or fewer")
        String name,
        Integer position
) {
}
