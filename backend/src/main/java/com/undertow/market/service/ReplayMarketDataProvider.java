package com.undertow.market.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Deterministic historical data with no scripted anomalies - purely the
 * baseline random walk. Used by the Phase 10 backtest module to validate
 * that persist/resolve logic behaves sensibly on data that was never
 * engineered to contain a signal, i.e. to check the false-positive rate.
 *
 * Uses a different seed from the demo scenario so the two never coincidentally
 * produce identical series.
 *
 * Always instantiated (not gated behind undertow.market.provider) - the
 * backtest module injects this by its CONCRETE type specifically so it works
 * independent of whichever provider is active/@Primary for the live app.
 * See docs/tradeoffs.md.
 */
@Component
public class ReplayMarketDataProvider implements MarketDataProvider {

    private static final long SEED = 1337L;
    private static final int MAX_LOOKBACK_DAYS = 120;

    private final LocalDate today;
    private final LocalDate windowStart;
    private final DeterministicMarketSimulator simulator;
    private final List<String> universe;

    public ReplayMarketDataProvider(SymbolDirectory symbolDirectory) {
        this.today = LocalDate.now();
        this.windowStart = today.minusDays(MAX_LOOKBACK_DAYS);
        this.simulator = new DeterministicMarketSimulator(
                SEED, symbolDirectory.search(null), windowStart, today, null);
        this.universe = symbolDirectory.search(null).stream().map(Symbol::ticker).toList();
    }

    @Override
    public String name() {
        return "replay";
    }

    @Override
    public LocalDate latestAvailableDate() {
        return today;
    }

    @Override
    public List<String> symbolUniverse() {
        return universe;
    }

    @Override
    public List<DailyObservation> history(String symbol, LocalDate from, LocalDate to) {
        LocalDate cappedFrom = from.isBefore(windowStart) ? windowStart : from;
        LocalDate cappedTo = to.isAfter(today) ? today : to;
        if (cappedTo.isBefore(cappedFrom)) {
            return List.of();
        }
        return simulator.history(symbol, cappedFrom, cappedTo);
    }
}
