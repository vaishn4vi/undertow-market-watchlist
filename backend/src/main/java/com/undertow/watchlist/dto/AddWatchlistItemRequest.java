package com.undertow.watchlist.dto;

import jakarta.validation.constraints.NotBlank;

public record AddWatchlistItemRequest(
        @NotBlank(message = "symbol must not be blank")
        String symbol
) {
}
