package com.undertow.watchlist.dto;

import java.time.Instant;
import java.util.UUID;

import com.undertow.watchlist.entity.WatchlistItem;

public record WatchlistItemResponse(
        UUID id,
        String symbol,
        String sector,
        int position,
        Instant addedAt
) {
    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
                item.getId(), item.getSymbol(), item.getSector(), item.getPosition(), item.getAddedAt());
    }
}
