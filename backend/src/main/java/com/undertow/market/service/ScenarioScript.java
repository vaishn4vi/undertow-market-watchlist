package com.undertow.market.service;

import java.time.LocalDate;

/**
 * Overlays scripted anomalies onto the baseline random walk. Implementations
 * only need to override the handful of (symbol, day) cells that matter for
 * the story; everything else falls through to ordinary beta-driven noise.
 * Null means pure baseline (used by Replay - no scripted anomalies at all).
 */
public interface ScenarioScript {

    /** Force a sector's daily shock to a specific value (e.g. the rally day). Null = no override. */
    Double overrideSectorShock(String sector, LocalDate day);

    /** Force a specific symbol's daily return to a specific value. Null = no override. */
    Double overrideReturn(String symbol, LocalDate day);

    /** True if this symbol has no data at all on this day (simulated feed outage). */
    boolean isOutage(String symbol, LocalDate day);

    /**
     * A small additive nudge to a symbol's idiosyncratic noise on a given day -
     * used to bias a signal toward mean-reverting (drift back to baseline) or
     * persisting (sustained drift away from baseline) over the away-window,
     * without hard-overriding the return outright. Zero = no bias.
     */
    double driftBias(String symbol, LocalDate day);
}
