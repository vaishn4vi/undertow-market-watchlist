package com.undertow.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.undertow.market.entity.MarketSnapshot;

public record MarketSnapshotResponse(
        String symbol,
        String sector,
        BigDecimal price,
        BigDecimal returnPct,
        BigDecimal sectorReturnPct,
        BigDecimal peerBasketReturnPct,
        String marketStatus,
        Instant asOf,
        boolean isCurrent // false when this is a carried-forward snapshot older than "today"
) {
    public static MarketSnapshotResponse from(MarketSnapshot snapshot, Instant currentAsOf) {
        return new MarketSnapshotResponse(
                snapshot.getSymbol(),
                snapshot.getSector(),
                snapshot.getPrice(),
                snapshot.getReturnPct(),
                snapshot.getSectorReturnPct(),
                snapshot.getPeerBasketReturnPct(),
                snapshot.getMarketStatus(),
                snapshot.getAsOf(),
                snapshot.getAsOf().equals(currentAsOf)
        );
    }
}
