package com.undertow.trust.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.undertow.market.entity.MarketObservation;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketObservationRepository;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.market.service.MarketDataProvider;
import com.undertow.trust.model.TrustStatus;

/**
 * The single place that decides how much to trust a symbol's current data.
 *
 * Algorithm: look at the MOST RECENT ingestion attempt for the symbol.
 *   - If it was flagged CONFLICTING at ingestion time (two disagreeing
 *     observations for the same day), trust is CONFLICTING - we do have an
 *     accepted value (first-write-wins, see MarketDataService), just with
 *     reduced confidence, since it was disputed.
 *   - If it was flagged UNAVAILABLE (the provider had no data that day),
 *     trust is UNAVAILABLE. Callers (SignalDetectionService) must not
 *     re-evaluate signals against carried-forward data when this is the
 *     case - see docs/tradeoffs.md ("why signals must not silently
 *     resolve/escalate on unavailable data").
 *   - Otherwise, the most recent attempt succeeded cleanly, so trust is a
 *     pure freshness classification: how many days behind is the latest
 *     accepted snapshot relative to what the market considers "today" right
 *     now (TrustClassifier, gap-based, not wall-clock-based).
 */
@Service
public class TrustService {

    private final MarketObservationRepository observationRepository;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketDataProvider provider;

    private final int delayedThresholdDays;
    private final double confidenceLive;
    private final double confidenceDelayed;
    private final double confidenceStale;
    private final double confidenceConflicting;
    private final double confidenceUnavailable;

    public TrustService(
            MarketObservationRepository observationRepository,
            MarketSnapshotRepository snapshotRepository,
            MarketDataProvider provider,
            @Value("${undertow.trust.delayed-threshold-days:1}") int delayedThresholdDays,
            @Value("${undertow.trust.confidence-live:1.0}") double confidenceLive,
            @Value("${undertow.trust.confidence-delayed:0.8}") double confidenceDelayed,
            @Value("${undertow.trust.confidence-stale:0.5}") double confidenceStale,
            @Value("${undertow.trust.confidence-conflicting:0.3}") double confidenceConflicting,
            @Value("${undertow.trust.confidence-unavailable:0.0}") double confidenceUnavailable
    ) {
        this.observationRepository = observationRepository;
        this.snapshotRepository = snapshotRepository;
        this.provider = provider;
        this.delayedThresholdDays = delayedThresholdDays;
        this.confidenceLive = confidenceLive;
        this.confidenceDelayed = confidenceDelayed;
        this.confidenceStale = confidenceStale;
        this.confidenceConflicting = confidenceConflicting;
        this.confidenceUnavailable = confidenceUnavailable;
    }

    /** Empty means "never ingested at all" - a different concept from UNAVAILABLE (attempted and failed). */
    public Optional<TrustAssessment> assess(String symbol) {
        Optional<MarketObservation> latestObs = observationRepository.findFirstBySymbolOrderByReceivedAtDesc(symbol);
        if (latestObs.isEmpty()) {
            return Optional.empty();
        }

        TrustStatus rawStatus = TrustStatus.valueOf(latestObs.get().getTrustStatus());

        if (rawStatus == TrustStatus.CONFLICTING) {
            return Optional.of(new TrustAssessment(TrustStatus.CONFLICTING, confidenceConflicting,
                    "Conflicting observations were received for this symbol; the first accepted value is being used."));
        }

        if (rawStatus == TrustStatus.UNAVAILABLE) {
            return Optional.of(new TrustAssessment(TrustStatus.UNAVAILABLE, confidenceUnavailable,
                    "Market feed unavailable. Previous data is being carried forward without re-evaluation."));
        }

        // The most recent attempt succeeded (LIVE at ingestion) - classify
        // by how fresh the resulting snapshot still is right now.
        Optional<MarketSnapshot> latestSnapshot = snapshotRepository.findTopBySymbolOrderByAsOfDesc(symbol);
        if (latestSnapshot.isEmpty()) {
            return Optional.empty(); // defensive: shouldn't happen if an accepted observation exists
        }

        LocalDate snapshotDate = latestSnapshot.get().getAsOf().atZone(ZoneId.of("America/New_York")).toLocalDate();
        LocalDate currentDate = provider.latestAvailableDate();
        long gapDays = TrustClassifier.gapDays(snapshotDate, currentDate);
        TrustStatus freshness = TrustClassifier.classifyFreshness(snapshotDate, currentDate, delayedThresholdDays);

        return switch (freshness) {
            case LIVE -> Optional.of(new TrustAssessment(TrustStatus.LIVE, confidenceLive,
                    "Verified as of the latest close."));
            case DELAYED -> Optional.of(new TrustAssessment(TrustStatus.DELAYED, confidenceDelayed,
                    "Source data is " + gapDays + " day(s) old."));
            case STALE -> Optional.of(new TrustAssessment(TrustStatus.STALE, confidenceStale,
                    "Source data is " + gapDays + " day(s) old and considered stale."));
            default -> Optional.of(new TrustAssessment(TrustStatus.LIVE, confidenceLive, "Verified as of the latest close."));
        };
    }
}
