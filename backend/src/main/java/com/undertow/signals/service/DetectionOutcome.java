package com.undertow.signals.service;

import java.util.List;

import com.undertow.signals.entity.SignalEvent;
import com.undertow.trust.model.TrustStatus;

/**
 * events is always the FULL set of currently-known signal events for the
 * symbol (unchanged from before this call when dataUnavailable is true) -
 * never a partial or newly-created-only list, so callers can render "here's
 * what we know" regardless of whether fresh detection ran this time.
 *
 * allCandidates is the full shadow evaluation (SignalEngine.evaluateAll()) -
 * one candidate per computable type regardless of whether it crossed its
 * detection gate, or empty when data was unavailable or history was
 * insufficient. The attention ledger (Phase 6) uses this to track a signal
 * type's continuous severity across check-ins, which is what makes
 * persistence, worsening, and mean-reversion resolution possible - events
 * alone only tell you when something newly crossed a gate, never when an
 * already-open episode's severity has changed.
 */
public record DetectionOutcome(
        List<SignalEvent> events,
        boolean dataUnavailable,
        TrustStatus trustStatus,
        double confidence,
        List<SignalCandidate> allCandidates
) {
}
