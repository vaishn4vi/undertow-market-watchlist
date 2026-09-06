package com.undertow.signals.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.market.service.MarketDataProvider;
import com.undertow.market.service.MarketDataService;
import com.undertow.signals.entity.SignalEvent;
import com.undertow.signals.entity.SignalEvidence;
import com.undertow.signals.repository.SignalEventRepository;
import com.undertow.signals.repository.SignalEvidenceRepository;
import com.undertow.trust.model.TrustStatus;
import com.undertow.trust.service.TrustAssessment;
import com.undertow.trust.service.TrustService;

/**
 * Bridges persisted market snapshots to the pure SignalEngine and persists
 * whatever it decides. This class owns zero detection logic itself - it only
 * shapes data in and shapes results out, so the engine remains the single
 * place that decides whether a signal exists (spec section 24).
 *
 * Detection is triggered explicitly (see SignalController) rather than
 * automatically from market ingestion, keeping the dependency direction
 * clean: signals depends on market, never the reverse. Automatic triggering
 * as part of a user's check-in is Phase 8's job (reconciliation), not this
 * service's.
 *
 * Confidence is derived from the symbol's actual trust assessment
 * (TrustService), which reads real ingestion provenance. When that
 * assessment comes back UNAVAILABLE, detection is skipped entirely rather
 * than re-evaluating stale carried-forward data: doing so risks silently
 * resolving or escalating a signal based on a "today" that isn't really
 * today (spec section 5's core resilience requirement).
 *
 * IMPORTANT: this method is the SHARED, symbol-scoped entry point every
 * caller (manual /detect, the attention ledger, reconciliation) ultimately
 * goes through, so it is responsible for guaranteeing its own data
 * precondition - ensuring enough history has actually been ingested for the
 * symbol - rather than assuming some other caller already did it. Ingestion
 * itself is fully idempotent (Phase 3), so calling it on every detection
 * request is safe and cheap once data already exists.
 */
@Service
public class SignalDetectionService {

    /** Comfortably covers rolling-window + min-history with margin, regardless of provider gaps. */
    private static final int HISTORY_BACKFILL_DAYS = 45;

    private final MarketSnapshotRepository snapshotRepository;
    private final SignalEventRepository signalEventRepository;
    private final SignalEvidenceRepository signalEvidenceRepository;
    private final TrustService trustService;
    private final MarketDataService marketDataService;
    private final MarketDataProvider marketDataProvider;
    private final ObjectMapper objectMapper;
    private final SignalEngine engine;

    public SignalDetectionService(
            MarketSnapshotRepository snapshotRepository,
            SignalEventRepository signalEventRepository,
            SignalEvidenceRepository signalEvidenceRepository,
            TrustService trustService,
            MarketDataService marketDataService,
            MarketDataProvider marketDataProvider,
            ObjectMapper objectMapper,
            @Value("${undertow.signals.rolling-window-days:20}") int rollingWindowDays,
            @Value("${undertow.signals.min-history-days:10}") int minHistoryDays,
            @Value("${undertow.signals.decoupling-z-threshold:2.0}") double decouplingZThreshold,
            @Value("${undertow.signals.silence-min-expected-return-pct:1.0}") double silenceMinExpectedReturnPct,
            @Value("${undertow.signals.silence-max-participation-ratio:0.25}") double silenceMaxParticipationRatio,
            @Value("${undertow.signals.historical-abnormality-percentile-threshold:90.0}") double abnormalityPercentileThreshold
    ) {
        this.snapshotRepository = snapshotRepository;
        this.signalEventRepository = signalEventRepository;
        this.signalEvidenceRepository = signalEvidenceRepository;
        this.trustService = trustService;
        this.marketDataService = marketDataService;
        this.marketDataProvider = marketDataProvider;
        this.objectMapper = objectMapper;
        this.engine = new SignalEngine(new SignalEngineConfig(
                rollingWindowDays, minHistoryDays, decouplingZThreshold,
                silenceMinExpectedReturnPct, silenceMaxParticipationRatio, abnormalityPercentileThreshold));
    }

    /**
     * Evaluates the most recent snapshot for a symbol against its prior
     * history and persists any new signals, using confidence derived from
     * the symbol's real trust assessment. Idempotent: re-running against the
     * same latest snapshot never creates duplicate signal_events rows.
     *
     * When the symbol's data is currently UNAVAILABLE, no evaluation runs at
     * all - the returned outcome reports the full existing signal history
     * completely untouched, with dataUnavailable=true so callers can tell
     * "checked, nothing new" apart from "couldn't check".
     */
    @Transactional
    public DetectionOutcome detectForSymbol(String symbol) {
        ensureHistoryIsIngested(symbol);

        Optional<TrustAssessment> assessment = trustService.assess(symbol);

        if (assessment.isPresent() && assessment.get().status() == TrustStatus.UNAVAILABLE) {
            return new DetectionOutcome(
                    signalEventRepository.findBySymbolOrderByDetectedAtDesc(symbol),
                    true,
                    TrustStatus.UNAVAILABLE,
                    assessment.get().confidence(),
                    List.of());
        }

        double confidence = assessment.map(TrustAssessment::confidence).orElse(1.0);
        TrustStatus status = assessment.map(TrustAssessment::status).orElse(TrustStatus.LIVE);

        // Bounded by the provider's current clock, not just "whatever the
        // last row in the table happens to be". A symbol can easily have
        // snapshots ingested for dates AFTER the provider's current
        // "today" - e.g. the demo clock gets reset to the rally day after
        // an earlier run already advanced it through the full away window
        // and ingested that later data. Without this bound, "today" would
        // silently resolve to that leftover future data (already
        // mean-reverted, no longer triggering) instead of the actual
        // current day, and re-running the demo scenario would stop
        // detecting anything at all after the first time.
        Instant todayClose = marketDataProvider.latestAvailableDate()
                .atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        List<MarketSnapshot> all = snapshotRepository.findBySymbolOrderByAsOfAsc(symbol).stream()
                .filter(s -> !s.getAsOf().isAfter(todayClose))
                .toList();
        if (all.size() < 2) {
            return new DetectionOutcome(List.of(), false, status, confidence, List.of()); // need at least one prior day plus "today"
        }

        MarketSnapshot todaySnapshot = all.get(all.size() - 1);
        List<MarketSnapshot> priorSnapshots = all.subList(0, all.size() - 1);

        List<HistoricalReturn> priorHistory = new ArrayList<>();
        for (MarketSnapshot s : priorSnapshots) {
            priorHistory.add(toHistoricalReturn(s));
        }
        HistoricalReturn today = toHistoricalReturn(todaySnapshot);

        List<SignalCandidate> allCandidates = engine.evaluateAll(priorHistory, today, confidence);
        List<SignalCandidate> triggered = allCandidates.stream().filter(SignalCandidate::triggered).toList();

        for (SignalCandidate candidate : triggered) {
            persistIfNew(symbol, candidate, todaySnapshot.getId());
        }

        return new DetectionOutcome(
                signalEventRepository.findBySymbolOrderByDetectedAtDesc(symbol), false, status, confidence, allCandidates);
    }

    /**
     * Backfills a generous window of history (idempotent - safe to call on
     * every detection request) and then explicitly re-checks "today"
     * specifically via the single-day path, which is what actually records
     * an UNAVAILABLE observation when the provider has no data for today -
     * ensureIngestedHistory's range query silently omits outage days rather
     * than recording them, since MarketDataProvider#history only returns
     * days that have data at all.
     *
     * Guarded to symbols the active provider actually knows about. Every
     * real watchlist symbol is already validated against this same universe
     * at the watchlist layer (SymbolDirectory), so this guard never affects
     * production behavior - it only prevents this method from recording a
     * spurious UNAVAILABLE observation for a symbol the provider has never
     * heard of, which would otherwise corrupt trust assessment for tests
     * (and any future caller) that manage market data directly rather than
     * through the provider.
     */
    private void ensureHistoryIsIngested(String symbol) {
        if (!marketDataProvider.symbolUniverse().contains(symbol)) {
            return;
        }
        LocalDate today = marketDataProvider.latestAvailableDate();
        LocalDate historyStart = today.minusDays(HISTORY_BACKFILL_DAYS);
        marketDataService.ensureIngestedHistory(symbol, historyStart, today);
        marketDataService.ensureIngestedAndGetLatest(symbol);
    }

    public List<SignalEvent> latestForSymbol(String symbol) {
        return signalEventRepository.findBySymbolOrderByDetectedAtDesc(symbol);
    }

    public Optional<SignalEvidence> evidenceFor(UUID signalEventId) {
        return signalEvidenceRepository.findBySignalEventId(signalEventId);
    }

    private SignalEvent persistIfNew(String symbol, SignalCandidate candidate, UUID snapshotId) {
        return signalEventRepository.findBySymbolAndTypeAndSnapshotId(symbol, candidate.type().name(), snapshotId)
                .orElseGet(() -> {
                    SignalEvent event = signalEventRepository.save(new SignalEvent(
                            symbol, candidate.type().name(), candidate.severity(),
                            bd(candidate.confidence(), 3), snapshotId));

                    signalEvidenceRepository.save(new SignalEvidence(
                            event.getId(),
                            bd(candidate.stockReturn(), 4),
                            bd(candidate.sectorReturn(), 4),
                            bd(candidate.expectedReturn(), 4),
                            bd(candidate.deviation(), 4),
                            bd(candidate.historicalPercentile(), 2),
                            toJson(candidate.extraEvidence())
                    ));
                    return event;
                });
    }

    private HistoricalReturn toHistoricalReturn(MarketSnapshot snapshot) {
        LocalDate date = snapshot.getAsOf().atZone(ZoneId.of("America/New_York")).toLocalDate();
        return new HistoricalReturn(date, snapshot.getReturnPct().doubleValue(), snapshot.getSectorReturnPct().doubleValue());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static BigDecimal bd(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}
