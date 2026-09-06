package com.undertow.market.service;

import java.time.Instant;
import java.time.LocalDate;

import com.undertow.trust.model.TrustStatus;

/**
 * A single provider-produced observation for one symbol on one day.
 *
 * Providers only ever produce LIVE observations here (or omit the day
 * entirely to represent an outage - see MarketDataProvider#history). The
 * ingestion layer is what may additionally tag CONFLICTING when two
 * observations for the same (symbol, date) disagree.
 */
public record DailyObservation(
        LocalDate date,
        String symbol,
        String sector,
        double price,
        double returnPct,
        double sectorReturnPct,
        double peerBasketReturnPct,
        MarketStatus marketStatus,
        Instant sourceTimestamp,
        TrustStatus trustStatus
) {
}
