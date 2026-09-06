package com.undertow.trust.model;

/**
 * Provenance/freshness state of a single market observation.
 *
 * Phase 3 (market ingestion) only ever produces LIVE or UNAVAILABLE, since the
 * demo/replay simulators either have a value for a given day or they don't,
 * plus CONFLICTING when two observations for the same (symbol, as_of)
 * disagree. DELAYED and STALE are freshness classifications that depend on
 * elapsed time since the source timestamp - that reconciliation logic
 * belongs to the trust module proper (Phase 5), not to ingestion.
 */
public enum TrustStatus {
    LIVE,
    DELAYED,
    STALE,
    UNAVAILABLE,
    CONFLICTING
}
