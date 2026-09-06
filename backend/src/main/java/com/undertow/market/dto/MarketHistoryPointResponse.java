package com.undertow.market.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.undertow.market.entity.MarketSnapshot;

public record MarketHistoryPointResponse(
        Instant asOf,
        BigDecimal price,
        BigDecimal returnPct,
        BigDecimal sectorReturnPct,
        BigDecimal peerBasketReturnPct
) {
    public static MarketHistoryPointResponse from(MarketSnapshot snapshot) {
        return new MarketHistoryPointResponse(
                snapshot.getAsOf(),
                snapshot.getPrice(),
                snapshot.getReturnPct(),
                snapshot.getSectorReturnPct(),
                snapshot.getPeerBasketReturnPct()
        );
    }
}
