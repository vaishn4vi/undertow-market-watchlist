package com.undertow.trust.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Aggregate trust picture across every symbol the user currently tracks
 * (union of items across all of their watchlists). This is a pure
 * aggregation over per-symbol TrustAssessment results already computed by
 * TrustService - it introduces no new trust logic of its own.
 *
 * overallStatus is the single worst status present among tracked symbols
 * (see DataTrustOverviewService for the severity ordering), so the headline
 * indicator never overstates freshness by averaging it away. If the user
 * tracks no symbols at all, overallStatus is "UNKNOWN".
 */
public record DataTrustOverviewResponse(
        String overallStatus,
        int totalSymbols,
        Map<String, Integer> distribution,
        Instant asOf
) {
}
