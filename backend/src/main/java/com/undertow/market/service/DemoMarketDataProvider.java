package com.undertow.market.service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Deterministic, seeded demo data source. Anchors "day 0" (the rally day) to
 * the date the server started, so a live demo always looks current without
 * depending on wall-clock time beyond that one anchor point - see
 * docs/tradeoffs.md ("why demo/replay provider").
 *
 * Exposes an advanceable clock (see fastForwardToReturnDay/resetToStart) so
 * the eventual "Run Demo Scenario" flow (Phase 12) can reveal the 12-day
 * story in one deliberate step, matching the product demo script.
 *
 * @Primary because this is the provider the rest of the app (market/signals/
 * trust controllers and services) depends on via the generic
 * MarketDataProvider interface. ReplayMarketDataProvider is also always a
 * bean (Phase 10's backtest module injects it by its CONCRETE type, not the
 * interface, specifically so backtesting works independent of whichever
 * provider is "active" for the live app - see docs/tradeoffs.md).
 */
@Component
@Primary
public class DemoMarketDataProvider implements MarketDataProvider {

    private static final long SEED = 42L;
    private static final int BASELINE_DAYS_BEFORE_RALLY = 30;

    private final LocalDate rallyDay;
    private final LocalDate windowStart;
    private final LocalDate windowEnd;
    private final DeterministicMarketSimulator simulator;
    private final List<String> universe;

    private final AtomicReference<LocalDate> clock;

    public DemoMarketDataProvider(SymbolDirectory symbolDirectory) {
        this.rallyDay = LocalDate.now();
        this.windowStart = rallyDay.minusDays(BASELINE_DAYS_BEFORE_RALLY);
        this.windowEnd = rallyDay.plusDays(HackathonDemoScenario.AWAY_WINDOW_DAYS);
        this.simulator = new DeterministicMarketSimulator(
                SEED, symbolDirectory.search(null), windowStart, windowEnd,
                new HackathonDemoScenario(rallyDay));
        this.universe = symbolDirectory.search(null).stream().map(Symbol::ticker).toList();
        this.clock = new AtomicReference<>(rallyDay);
    }

    @Override
    public String name() {
        return "demo";
    }

    @Override
    public LocalDate latestAvailableDate() {
        return clock.get();
    }

    @Override
    public List<String> symbolUniverse() {
        return universe;
    }

    @Override
    public List<DailyObservation> history(String symbol, LocalDate from, LocalDate to) {
        LocalDate cappedTo = to.isAfter(clock.get()) ? clock.get() : to;
        if (cappedTo.isBefore(from)) {
            return List.of();
        }
        return simulator.history(symbol, from, cappedTo);
    }

    /** Rewinds the demo clock to the rally day - "normal watchlist" state before the story starts. */
    public LocalDate resetToStart() {
        clock.set(rallyDay);
        return clock.get();
    }

    /** Jumps the demo clock to the return day, revealing the full 12-day story at once. */
    public LocalDate fastForwardToReturnDay() {
        clock.set(windowEnd);
        return clock.get();
    }

    /** Advances the demo clock by a number of days, capped at the return day. */
    public LocalDate advance(int days) {
        return clock.updateAndGet(current -> {
            LocalDate next = current.plusDays(days);
            return next.isAfter(windowEnd) ? windowEnd : next;
        });
    }

    public LocalDate rallyDay() {
        return rallyDay;
    }

    public LocalDate returnDay() {
        return windowEnd;
    }
}
