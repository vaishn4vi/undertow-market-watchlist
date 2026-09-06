package com.undertow.signals.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.undertow.signals.model.SignalType;

/**
 * Detects DECOUPLING, SILENCE, and HISTORICAL_ABNORMALITY from a stock's
 * return history plus today's observation. Every formula here matches the
 * approved design spec exactly - this class makes the actual decision;
 * nothing downstream is allowed to second-guess it (spec section 24: AI may
 * only explain evidence this class already produced).
 *
 * DECOUPLING and SILENCE are mutually exclusive by construction: decoupling
 * requires the stock to move in the *strictly opposite* direction from what
 * the sector relationship predicts, while silence requires it to move in the
 * *same* direction (or not at all) but far less than predicted. A day can
 * never satisfy both.
 */
public class SignalEngine {

    private static final double VARIANCE_EPSILON = 1e-9;
    private static final double SEVERITY_Z_CAP = 4.0;
    private static final double SEVERITY_SECTOR_DEVIATION_CAP_PCT = 8.0;

    private static final double SEVERITY_WEIGHT_MOVEMENT = 0.40;
    private static final double SEVERITY_WEIGHT_SECTOR_DEVIATION = 0.30;
    private static final double SEVERITY_WEIGHT_HISTORICAL_PERCENTILE = 0.20;
    private static final double SEVERITY_WEIGHT_CONFIDENCE = 0.10;

    private final SignalEngineConfig config;

    public SignalEngine() {
        this(SignalEngineConfig.defaults());
    }

    public SignalEngine(SignalEngineConfig config) {
        this.config = config;
    }

    /**
     * @param priorHistory ascending by date, must NOT include today
     * @param today        today's observation
     * @param confidence   0-1, reflects the trust status of today's market
     *                     data
     * @return only TRIGGERED candidates - identical to calling evaluateAll()
     *         and filtering by triggered(). This is the method Phase 4/5
     *         persistence logic uses; its behavior is completely unchanged
     *         from before evaluateAll() existed (see that method for why).
     */
    public List<SignalCandidate> evaluate(List<HistoricalReturn> priorHistory, HistoricalReturn today, double confidence) {
        return evaluateAll(priorHistory, today, confidence).stream()
                .filter(SignalCandidate::triggered)
                .toList();
    }

    /**
     * Like evaluate(), but returns one candidate per computable type
     * regardless of whether it crossed its detection threshold, tagged with
     * triggered=true/false. This does NOT change what counts as a detected
     * signal - triggered reflects exactly the same gate conditions evaluate()
     * has always used. It exists because the attention ledger (Phase 6)
     * needs to track a signal's *continuous* severity across check-ins to
     * detect persistence, worsening, and mean-reversion resolution - even on
     * days where the signal no longer crosses its original detection gate.
     * Without this, there would be no way to observe "severity has fallen
     * back to normal" once a signal stops being flagged.
     *
     * @return up to 3 candidates (decoupling-or-not, silence-or-not,
     *         abnormality-or-not), or empty if there isn't enough history.
     */
    public List<SignalCandidate> evaluateAll(List<HistoricalReturn> priorHistory, HistoricalReturn today, double confidence) {
        List<SignalCandidate> results = new ArrayList<>();
        double safeConfidence = Math.max(0.0, Math.min(1.0, confidence));

        if (priorHistory.size() < config.minHistoryDays()) {
            return results; // insufficient history - deliberately silent, not an error
        }

        List<HistoricalReturn> window = lastN(priorHistory, config.rollingWindowDays());

        double[] sectorReturns = window.stream().mapToDouble(HistoricalReturn::sectorReturn).toArray();
        double[] stockReturns = window.stream().mapToDouble(HistoricalReturn::stockReturn).toArray();

        StatisticsUtil.OlsResult ols = StatisticsUtil.regress(sectorReturns, stockReturns);
        double expectedReturn = ols.alpha() + ols.beta() * today.sectorReturn();
        double residual = today.stockReturn() - expectedReturn;

        double[] historicalResiduals = new double[window.size()];
        for (int i = 0; i < window.size(); i++) {
            HistoricalReturn h = window.get(i);
            historicalResiduals[i] = h.stockReturn() - (ols.alpha() + ols.beta() * h.sectorReturn());
        }
        double residualMean = StatisticsUtil.mean(historicalResiduals);
        double residualStdev = StatisticsUtil.stdev(historicalResiduals);

        double[] historicalAbsReturns = window.stream().mapToDouble(h -> Math.abs(h.stockReturn())).toArray();
        double histAbsMean = StatisticsUtil.mean(historicalAbsReturns);
        double histAbsStdev = StatisticsUtil.stdev(historicalAbsReturns);
        double todayAbsReturn = Math.abs(today.stockReturn());
        double historicalPercentile = StatisticsUtil.percentileRank(todayAbsReturn, historicalAbsReturns);

        double sectorDeviation = Math.abs(today.stockReturn() - today.sectorReturn());

        // residualZ feeds DECOUPLING's gate directly and both detectors'
        // severity. When residual variance is genuinely zero (a perfect,
        // noiseless historical fit), fall back to a signed sentinel rather
        // than skipping detection entirely - mirrors the same fallback used
        // for dailyMoveZ below, and keeps SILENCE (whose actual gate is the
        // participation ratio, not this z-score) fully independent of
        // whether residual variance happens to be computable.
        double residualZ;
        if (residualStdev > VARIANCE_EPSILON) {
            residualZ = (residual - residualMean) / residualStdev;
        } else {
            double centeredResidual = residual - residualMean;
            residualZ = centeredResidual > VARIANCE_EPSILON ? SEVERITY_Z_CAP
                    : centeredResidual < -VARIANCE_EPSILON ? -SEVERITY_Z_CAP : 0.0;
        }

        results.add(evaluateDecoupling(today, expectedReturn, residual, residualZ, sectorDeviation,
                historicalPercentile, safeConfidence, ols));

        results.add(evaluateSilence(today, expectedReturn, residual, residualZ, sectorDeviation,
                historicalPercentile, safeConfidence, ols));

        results.add(evaluateHistoricalAbnormality(today, expectedReturn, residual, historicalPercentile, todayAbsReturn,
                histAbsMean, histAbsStdev, sectorDeviation, safeConfidence, ols));

        return results;
    }

    private SignalCandidate evaluateDecoupling(
            HistoricalReturn today, double expectedReturn, double residual, double residualZ,
            double sectorDeviation, double historicalPercentile, double confidence, StatisticsUtil.OlsResult ols) {

        double actual = today.stockReturn();
        double absResidualZ = Math.abs(residualZ);

        // Opposite non-zero signs only - a flat/zero actual return is never
        // "decoupling", it's silence's territory (see class-level note).
        boolean strictlyOppositeSign = Math.signum(actual) * Math.signum(expectedReturn) < 0;
        boolean triggered = absResidualZ >= config.decouplingZThreshold() && strictlyOppositeSign;

        int severity = computeSeverity(absResidualZ, sectorDeviation, historicalPercentile, confidence);
        Map<String, Double> extra = new LinkedHashMap<>();
        extra.put("residualZ", round(residualZ));
        extra.put("beta", round(ols.beta()));
        extra.put("alpha", round(ols.alpha()));
        return new SignalCandidate(
                SignalType.DECOUPLING, severity, confidence, round(actual), round(today.sectorReturn()),
                round(expectedReturn), round(residual), round(historicalPercentile), extra, triggered);
    }

    private SignalCandidate evaluateSilence(
            HistoricalReturn today, double expectedReturn, double residual, double residualZ,
            double sectorDeviation, double historicalPercentile, double confidence, StatisticsUtil.OlsResult ols) {

        double actual = today.stockReturn();
        double absResidualZ = Math.abs(residualZ);

        // Guard true division-by-zero separately from the "is this expected
        // move even large enough to be meaningful" gate below - a near-zero
        // expectedReturn must never produce NaN/Infinity in the shadow
        // candidate, even when triggered will end up false regardless.
        double participationRatio = Math.abs(expectedReturn) > VARIANCE_EPSILON ? actual / expectedReturn : 0.0;

        boolean magnitudeGateMet = Math.abs(expectedReturn) >= config.silenceMinExpectedReturnPct();
        boolean ratioGateMet = participationRatio >= 0.0 && participationRatio <= config.silenceMaxParticipationRatio();
        boolean triggered = magnitudeGateMet && ratioGateMet;

        int severity = computeSeverity(absResidualZ, sectorDeviation, historicalPercentile, confidence);
        Map<String, Double> extra = new LinkedHashMap<>();
        extra.put("residualZ", round(residualZ));
        extra.put("beta", round(ols.beta()));
        extra.put("alpha", round(ols.alpha()));
        extra.put("participationRatio", round(participationRatio));
        return new SignalCandidate(
                SignalType.SILENCE, severity, confidence, round(actual), round(today.sectorReturn()),
                round(expectedReturn), round(residual), round(historicalPercentile), extra, triggered);
    }

    private SignalCandidate evaluateHistoricalAbnormality(
            HistoricalReturn today, double expectedReturn, double residual, double historicalPercentile,
            double todayAbsReturn, double histAbsMean, double histAbsStdev, double sectorDeviation,
            double confidence, StatisticsUtil.OlsResult ols) {

        boolean triggered = historicalPercentile >= config.historicalAbnormalityPercentileThreshold();

        double dailyMoveZ;
        if (histAbsStdev > VARIANCE_EPSILON) {
            dailyMoveZ = (todayAbsReturn - histAbsMean) / histAbsStdev;
        } else {
            // Every historical day moved by an identical amount - no
            // variance to divide by. Today either broke that pattern
            // (treat as maximally abnormal) or didn't (not abnormal), rather
            // than producing NaN/Infinity from a zero denominator.
            dailyMoveZ = todayAbsReturn > histAbsMean ? SEVERITY_Z_CAP : 0.0;
        }

        int severity = computeSeverity(Math.abs(dailyMoveZ), sectorDeviation, historicalPercentile, confidence);
        Map<String, Double> extra = new LinkedHashMap<>();
        extra.put("dailyMoveZ", round(dailyMoveZ));
        extra.put("beta", round(ols.beta()));
        extra.put("alpha", round(ols.alpha()));

        return new SignalCandidate(
                SignalType.HISTORICAL_ABNORMALITY, severity, confidence, round(today.stockReturn()),
                round(today.sectorReturn()), round(expectedReturn), round(residual),
                round(historicalPercentile), extra, triggered);
    }

    /**
     * severity = 40% relative movement abnormality + 30% sector deviation
     *          + 20% historical abnormality + 10% data confidence,
     * each normalized to [0,1] before weighting - matches the approved
     * design spec weighting exactly.
     */
    private int computeSeverity(double zAbs, double sectorDeviationAbsPct, double historicalPercentile0to100, double confidence0to1) {
        double normZ = Math.min(zAbs, SEVERITY_Z_CAP) / SEVERITY_Z_CAP;
        double normSectorDev = Math.min(sectorDeviationAbsPct, SEVERITY_SECTOR_DEVIATION_CAP_PCT) / SEVERITY_SECTOR_DEVIATION_CAP_PCT;
        double normPercentile = historicalPercentile0to100 / 100.0;

        double raw = SEVERITY_WEIGHT_MOVEMENT * normZ
                + SEVERITY_WEIGHT_SECTOR_DEVIATION * normSectorDev
                + SEVERITY_WEIGHT_HISTORICAL_PERCENTILE * normPercentile
                + SEVERITY_WEIGHT_CONFIDENCE * confidence0to1;

        int severity = (int) Math.round(raw * 100);
        return Math.max(0, Math.min(100, severity));
    }

    private static List<HistoricalReturn> lastN(List<HistoricalReturn> history, int n) {
        if (history.size() <= n) return history;
        return history.subList(history.size() - n, history.size());
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
