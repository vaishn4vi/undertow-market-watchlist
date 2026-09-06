package com.undertow.market.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Source of market data. The rest of the application (signal engine,
 * attention ledger, etc.) depends only on this interface, never on a
 * concrete provider - see docs/tradeoffs.md ("why demo/replay provider").
 *
 * A missing day in the returned list is not an error: it represents the
 * provider genuinely having no data for that symbol on that day (a feed
 * outage in Demo, or simply outside the generated window in Replay). Callers
 * must treat an absent day as "unavailable", never as "zero movement".
 */
public interface MarketDataProvider {

    String name();

    /** The most recent date this provider currently has data through. */
    LocalDate latestAvailableDate();

    /** Every symbol this provider can generate/serve observations for. */
    List<String> symbolUniverse();

    /**
     * Observations for one symbol across [from, to] inclusive, ordered by
     * date ascending. Days with no data (outages) are simply absent from
     * the list - never represented as a zero-return entry.
     */
    List<DailyObservation> history(String symbol, LocalDate from, LocalDate to);

    /** Convenience for a single day; empty when that day is unavailable. */
    default Optional<DailyObservation> observationOn(String symbol, LocalDate date) {
        return history(symbol, date, date).stream().findFirst();
    }
}
