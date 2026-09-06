package com.undertow.signals.service;

/**
 * All the tunable constants from the approved signal engine design, in one
 * place with the approved defaults. Deliberately a plain record (not a
 * Spring @ConfigurationProperties class) so SignalEngine itself stays a pure,
 * dependency-free class - see docs/tradeoffs.md ("why deterministic signals
 * instead of ML" / pure-core pattern shared with DeterministicMarketSimulator).
 * The Spring-facing SignalDetectionService is what reads these from
 * application.yml and constructs this config.
 */
public record SignalEngineConfig(
        int rollingWindowDays,
        int minHistoryDays,
        double decouplingZThreshold,
        double silenceMinExpectedReturnPct,
        double silenceMaxParticipationRatio,
        double historicalAbnormalityPercentileThreshold
) {
    public static SignalEngineConfig defaults() {
        return new SignalEngineConfig(20, 10, 2.0, 1.0, 0.25, 90.0);
    }
}
