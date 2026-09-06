package com.undertow.market.service;

import java.time.LocalDate;

/**
 * Encodes the exact demo story from the product spec:
 *
 *   Day 0            Technology sector rallies +4.2%.
 *   Day 0            BHRT decouples (-2.7% against a sector rally - wrong direction).
 *   Day 0            GNGS goes silent (+0.3% when its beta implies ~+3%).
 *   Day 1..11        "away" window - BHRT gradually mean-reverts, GNGS stays quiet.
 *   Day 12           ARGY's feed goes unavailable (return-day data outage).
 *
 * The exact severity/debt numbers quoted in the product demo script are
 * illustrative and will be finalized from this real generator's output once
 * the signal engine and attention debt modules (Phases 4/7) are wired up -
 * they are not hardcoded anywhere.
 */
public class HackathonDemoScenario implements ScenarioScript {

    public static final String RALLY_SECTOR = "Technology";
    public static final String DECOUPLER_SYMBOL = "BHRT";
    public static final String SILENT_SYMBOL = "GNGS";
    public static final String OUTAGE_SYMBOL = "ARGY";
    public static final int AWAY_WINDOW_DAYS = 12;

    private final LocalDate rallyDay;
    private final LocalDate outageDay;

    public HackathonDemoScenario(LocalDate rallyDay) {
        this.rallyDay = rallyDay;
        this.outageDay = rallyDay.plusDays(AWAY_WINDOW_DAYS);
    }

    @Override
    public Double overrideSectorShock(String sector, LocalDate day) {
        if (sector.equals(RALLY_SECTOR) && day.equals(rallyDay)) {
            return 4.2;
        }
        return null;
    }

    @Override
    public Double overrideReturn(String symbol, LocalDate day) {
        if (!day.equals(rallyDay)) {
            return null;
        }
        if (symbol.equals(DECOUPLER_SYMBOL)) {
            return -2.7; // moves against a sector rally - DECOUPLING
        }
        if (symbol.equals(SILENT_SYMBOL)) {
            return 0.3; // barely moves despite the rally - SILENCE
        }
        return null;
    }

    @Override
    public boolean isOutage(String symbol, LocalDate day) {
        return symbol.equals(OUTAGE_SYMBOL) && day.equals(outageDay);
    }

    @Override
    public double driftBias(String symbol, LocalDate day) {
        boolean inAwayWindow = day.isAfter(rallyDay) && !day.isAfter(outageDay);
        if (!inAwayWindow) {
            return 0.0;
        }
        double daysSinceRally = java.time.temporal.ChronoUnit.DAYS.between(rallyDay, day);
        double progress = daysSinceRally / AWAY_WINDOW_DAYS; // 0 -> just after rally, 1 -> return day

        if (symbol.equals(DECOUPLER_SYMBOL)) {
            // Sustained negative excess residual right after the rally,
            // decaying linearly to zero by the return day - large enough to
            // dominate the ~0.45% daily noise floor, so a rolling-window
            // detector reads this as a clean resolve rather than lingering
            // in the noise band indefinitely.
            double startExcess = -1.8;
            return startExcess * (1 - progress);
        }
        if (symbol.equals(SILENT_SYMBOL)) {
            // Sustained suppression for the entire window, not decaying -
            // this signal should still read as active/persisted when the
            // user returns.
            return -1.0;
        }
        return 0.0;
    }
}
