package com.undertow.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWatchlistRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 100, message = "name must be 100 characters or fewer")
        String name
) {
}
