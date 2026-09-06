package com.undertow.signals;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import org.junit.jupiter.api.Test;

import com.undertow.signals.model.SignalType;
import com.undertow.signals.service.HistoricalReturn;
import com.undertow.signals.service.SignalCandidate;
import com.undertow.signals.service.SignalEngine;
import com.undertow.signals.service.StatisticsUtil;

/**
 * All scenarios here were first validated with a standalone harness run
 * outside the test suite (no Spring/Maven needed for this class - it's pure
 * logic) to confirm the actual numbers before asserting on them. Two real
 * bugs were caught that way before landing here: silence was incorrectly
 * gated behind decoupling's residual-variance check, and an early synthetic
 * decoupling case accidentally had zero residual variance (a coincidence of
 * clean test data, not a real scenario) and silently detected nothing.
 */
class SignalEngineTest {

    private final SignalEngine engine = new SignalEngine();

    // Small symmetric noise (mean ~0, stdev ~0.06) layered on a 1:1
    // stock-tracks-sector relationship - enough residual variance to make
    // z-scores meaningful without swamping the beta fit.
    private static final double[] NOISE_CYCLE = {0.05, -0.05, 0.08, -0.08, 0.03, -0.03, 0.06, -0.06, 0.02, -0.02};

    @Test
    void normalMovementProducesNoSignals() {
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), 0.55, 0.5);

        assertThat(engine.evaluate(history, today, 1.0)).isEmpty();
    }

    @Test
    void decouplingFiresWhenStockMovesOppositeToARallyingSector() {
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);

        List<SignalCandidate> signals = engine.evaluate(history, today, 1.0);

        assertThat(signals).extracting(SignalCandidate::type).contains(SignalType.DECOUPLING);
        SignalCandidate decoupling = signals.stream().filter(s -> s.type() == SignalType.DECOUPLING).findFirst().orElseThrow();
        assertThat(decoupling.deviation()).isNegative(); // actual underperformed expected
        assertThat(decoupling.severity()).isBetween(0, 100);
        assertThat(decoupling.extraEvidence()).containsKeys("residualZ", "beta", "alpha");
    }

    @Test
    void silenceFiresWhenStockBarelyParticipatesInARallyingSector() {
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), 0.2, 4.2);

        List<SignalCandidate> signals = engine.evaluate(history, today, 1.0);

        assertThat(signals).extracting(SignalCandidate::type).containsExactly(SignalType.SILENCE);
        SignalCandidate silence = signals.get(0);
        assertThat(silence.extraEvidence()).containsKey("participationRatio");
        assertThat(silence.extraEvidence().get("participationRatio")).isLessThanOrEqualTo(0.25);
    }

    @Test
    void decouplingAndSilenceAreMutuallyExclusive() {
        // Same-day evaluation can never produce both - proven here by
        // construction (opposite sign vs same-direction-but-muted are
        // disjoint), not just by control-flow ordering.
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);

        HistoricalReturn decouplingDay = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);
        HistoricalReturn silenceDay = new HistoricalReturn(LocalDate.now(), 0.2, 4.2);

        long decouplingAndSilenceCount = engine.evaluate(history, decouplingDay, 1.0).stream()
                .filter(s -> s.type() == SignalType.DECOUPLING || s.type() == SignalType.SILENCE)
                .count();
        long silenceOnlyCount = engine.evaluate(history, silenceDay, 1.0).stream()
                .filter(s -> s.type() == SignalType.DECOUPLING || s.type() == SignalType.SILENCE)
                .count();

        assertThat(decouplingAndSilenceCount).isEqualTo(1);
        assertThat(silenceOnlyCount).isEqualTo(1);
    }

    @Test
    void historicalAbnormalityFiresOnAnUnusuallyLargeMoveEvenWithoutASectorMove() {
        List<HistoricalReturn> history = buildHistory(20, i -> 0.3 * dayFactor(i), i -> 0.0);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), 3.8, 0.1);

        List<SignalCandidate> signals = engine.evaluate(history, today, 1.0);

        assertThat(signals).extracting(SignalCandidate::type).contains(SignalType.HISTORICAL_ABNORMALITY);
        SignalCandidate abnormality = signals.stream()
                .filter(s -> s.type() == SignalType.HISTORICAL_ABNORMALITY).findFirst().orElseThrow();
        assertThat(abnormality.historicalPercentile()).isGreaterThanOrEqualTo(90.0);
        assertThat(abnormality.extraEvidence()).containsKey("dailyMoveZ");
    }

    @Test
    void abnormalityCanCoOccurWithDecoupling() {
        // A move sharp enough to decouple from the sector is very likely
        // also unusual for the stock's own history - both can legitimately
        // fire the same day, unlike decoupling/silence which cannot.
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);

        List<SignalType> types = engine.evaluate(history, today, 1.0).stream().map(SignalCandidate::type).toList();

        assertThat(types).contains(SignalType.DECOUPLING, SignalType.HISTORICAL_ABNORMALITY);
    }

    @Test
    void insufficientHistoryProducesNoSignalsRatherThanUnreliableStatistics() {
        List<HistoricalReturn> shortHistory = buildHistory(5, i -> 1.0, i -> 1.0); // fewer than minHistoryDays=10
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);

        assertThat(engine.evaluate(shortHistory, today, 1.0)).isEmpty();
    }

    @Test
    void exactlyMinimumHistoryIsSufficientBoundary() {
        // minHistoryDays=10 is a ">=", not a ">" - exactly 10 must be usable.
        List<HistoricalReturn> exactlyMinHistory = buildNoisyHistory(10, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);

        assertThat(engine.evaluate(exactlyMinHistory, today, 1.0)).isNotEmpty();
    }

    @Test
    void zeroResidualVarianceDoesNotCrashAndStillAllowsAbnormality() {
        // Perfectly noiseless historical fit (stock == sector, constant,
        // every day) - residual variance is genuinely zero. Must not divide
        // by zero, and historical abnormality (which doesn't depend on
        // residual variance at all) should still be computable.
        List<HistoricalReturn> perfectFitHistory = buildHistory(20, i -> 1.0, i -> 1.0);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -5.0, 5.0);

        List<SignalCandidate> signals = engine.evaluate(perfectFitHistory, today, 1.0);

        assertThat(signals).extracting(SignalCandidate::type).contains(SignalType.HISTORICAL_ABNORMALITY);
    }

    @Test
    void zeroResidualVarianceStillAllowsSilenceToFire() {
        // Regression test for a real bug: silence's gate (participation
        // ratio) does not depend on residual variance, but was previously
        // gated behind the same check as decoupling's z-score, so it never
        // fired when residual variance happened to be exactly zero.
        List<HistoricalReturn> perfectFitHistory = buildHistory(20, i -> 1.0, i -> 1.0);
        HistoricalReturn mutedParticipationToday = new HistoricalReturn(LocalDate.now(), 0.1, 5.0);

        List<SignalCandidate> signals = engine.evaluate(perfectFitHistory, mutedParticipationToday, 1.0);

        assertThat(signals).extracting(SignalCandidate::type).contains(SignalType.SILENCE);
    }

    @Test
    void zeroSectorVarianceFallsBackToFlatModelWithoutCrashing() {
        // Sector return is constant (zero variance in x) - regression must
        // fall back to a flat model (beta=0) rather than dividing by zero.
        double[] alternating = {0.5, -0.5, 0.5, -0.5, 0.5, -0.5, 0.5, -0.5, 0.5, -0.5};
        List<HistoricalReturn> history = buildHistory(alternating.length, i -> alternating[i], i -> 0.0);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), 0.6, 0.0);

        List<SignalCandidate> signals = engine.evaluate(history, today, 1.0);

        // expectedReturn ends up ~0 (flat model), so neither decoupling nor
        // silence can gate meaningfully - just confirming no exception and
        // a sane result rather than NaN/Infinity anywhere.
        for (SignalCandidate c : signals) {
            assertThat(Double.isNaN(c.expectedReturn())).isFalse();
            assertThat(Double.isInfinite(c.expectedReturn())).isFalse();
        }
    }

    @Test
    void decouplingZScoreThresholdIsInclusiveAtExactly2Point0() {
        double[] stockHist = {0.5, 0.5, 0.5, 0.5, 0.5, 1.5, 1.5, 1.5, 1.5, 1.5}; // mean=1.0
        double stdev = StatisticsUtil.stdev(stockHist);
        List<HistoricalReturn> history = buildHistory(stockHist.length, i -> stockHist[i], i -> 0.0);
        double expected = 1.0; // alpha=mean=1.0, beta=0 (sector constant)

        double actualAtExactly2 = expected - 2.0 * stdev;
        double actualJustBelow2 = expected - 1.99 * stdev;

        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), actualAtExactly2, 0.0), 1.0))
                .extracting(SignalCandidate::type).contains(SignalType.DECOUPLING);
        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), actualJustBelow2, 0.0), 1.0))
                .extracting(SignalCandidate::type).doesNotContain(SignalType.DECOUPLING);
    }

    @Test
    void silenceParticipationRatioThresholdIsInclusiveAtExactly0Point25() {
        double[] ratioStockHist = {3, 3, 3, 3, 3, 5, 5, 5, 5, 5}; // mean = 4.0
        List<HistoricalReturn> history = buildHistory(ratioStockHist.length, i -> ratioStockHist[i], i -> 0.0);
        double expected = 4.0;

        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), expected * 0.25, 0.0), 1.0))
                .extracting(SignalCandidate::type).contains(SignalType.SILENCE);
        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), expected * 0.26, 0.0), 1.0))
                .extracting(SignalCandidate::type).doesNotContain(SignalType.SILENCE);
    }

    @Test
    void historicalAbnormalityPercentileThresholdIsInclusiveAtExactly90() {
        double[] absReturns = {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        List<HistoricalReturn> history = buildHistory(absReturns.length, i -> absReturns[i], i -> 0.0);

        // 0.9 is the 9th of 10 historical values -> percentile exactly 90.
        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), 0.9, 0.0), 1.0))
                .extracting(SignalCandidate::type).contains(SignalType.HISTORICAL_ABNORMALITY);
        // 0.85 sits at percentile 80 - below threshold.
        assertThat(engine.evaluate(history, new HistoricalReturn(LocalDate.now(), 0.85, 0.0), 1.0))
                .extracting(SignalCandidate::type).doesNotContain(SignalType.HISTORICAL_ABNORMALITY);
    }

    @Test
    void severityIsAlwaysWithinZeroToOneHundred() {
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        List<HistoricalReturn> extremeCases = List.of(
                new HistoricalReturn(LocalDate.now(), -50.0, 50.0),
                new HistoricalReturn(LocalDate.now(), 0.001, 0.001),
                new HistoricalReturn(LocalDate.now(), 0.0, 0.0)
        );
        for (HistoricalReturn today : extremeCases) {
            for (SignalCandidate c : engine.evaluate(history, today, 1.0)) {
                assertThat(c.severity()).isBetween(0, 100);
            }
        }
    }

    @Test
    void confidenceIsClampedIntoZeroToOneRange() {
        List<HistoricalReturn> history = buildNoisyHistory(20, this::dayFactor);
        HistoricalReturn today = new HistoricalReturn(LocalDate.now(), -2.7, 4.2);

        List<SignalCandidate> withOverConfidence = engine.evaluate(history, today, 5.0);
        List<SignalCandidate> withNegativeConfidence = engine.evaluate(history, today, -5.0);

        assertThat(withOverConfidence).allMatch(c -> c.confidence() <= 1.0);
        assertThat(withNegativeConfidence).allMatch(c -> c.confidence() >= 0.0);
    }

    private double dayFactor(int i) {
        return (i % 5 == 0) ? 1.3 : (i % 3 == 0 ? 0.7 : 1.0);
    }

    private List<HistoricalReturn> buildNoisyHistory(int days, IntToDoubleFunction sector) {
        List<HistoricalReturn> history = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days);
        for (int i = 0; i < days; i++) {
            double sectorReturn = sector.applyAsDouble(i);
            double stockReturn = sectorReturn + NOISE_CYCLE[i % NOISE_CYCLE.length];
            history.add(new HistoricalReturn(start.plusDays(i), stockReturn, sectorReturn));
        }
        return history;
    }

    private List<HistoricalReturn> buildHistory(int days, IntToDoubleFunction stock, IntToDoubleFunction sector) {
        List<HistoricalReturn> history = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(days);
        for (int i = 0; i < days; i++) {
            history.add(new HistoricalReturn(start.plusDays(i), stock.applyAsDouble(i), sector.applyAsDouble(i)));
        }
        return history;
    }
}
