package com.undertow.trust.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.undertow.trust.model.TrustStatus;

/**
 * Classifies how fresh a snapshot is, given how many calendar days behind
 * the provider's current date it is. Deliberately simple and gap-based
 * rather than wall-clock-based (comparing receivedAt to sourceTimestamp):
 * this system ingests data lazily on demand, so "how long ago did we happen
 * to fetch it" says nothing about whether the DATA itself is current. What
 * matters is whether the snapshot represents the market's most recent
 * trading day, or an older one.
 *
 * This method never returns UNAVAILABLE or CONFLICTING - those aren't
 * freshness classifications, they're provenance facts recorded at ingestion
 * time (see MarketDataService). This only distinguishes LIVE from DELAYED
 * from STALE among snapshots that were themselves accepted without issue.
 */
public final class TrustClassifier {

    private TrustClassifier() {
    }

    public static TrustStatus classifyFreshness(LocalDate snapshotAsOf, LocalDate currentDate, int delayedThresholdDays) {
        long gapDays = ChronoUnit.DAYS.between(snapshotAsOf, currentDate);
        if (gapDays <= 0) {
            return TrustStatus.LIVE;
        }
        if (gapDays <= delayedThresholdDays) {
            return TrustStatus.DELAYED;
        }
        return TrustStatus.STALE;
    }

    public static long gapDays(LocalDate snapshotAsOf, LocalDate currentDate) {
        return Math.max(0, ChronoUnit.DAYS.between(snapshotAsOf, currentDate));
    }
}
