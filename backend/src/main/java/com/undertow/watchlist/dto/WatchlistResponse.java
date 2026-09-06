package com.undertow.watchlist.dto;

import java.util.UUID;

import com.undertow.watchlist.entity.Watchlist;

public record WatchlistResponse(UUID id, String name, int position, int itemCount) {
    public static WatchlistResponse from(Watchlist watchlist, int itemCount) {
        return new WatchlistResponse(watchlist.getId(), watchlist.getName(), watchlist.getPosition(), itemCount);
    }
}
