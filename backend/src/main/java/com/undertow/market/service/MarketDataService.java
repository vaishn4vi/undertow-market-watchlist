package com.undertow.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undertow.market.entity.MarketObservation;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketObservationRepository;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.trust.model.TrustStatus;

/**
 * Bridges a MarketDataProvider to persisted state. This is the resilience
 * layer for market data (spec section 20): every ingestion attempt is
 * idempotent, conflicting re-observations are detected and recorded without
 * corrupting the accepted snapshot, and outages are logged as provenance
 * with no snapshot created at all - callers naturally see the last known
 * snapshot until a new one arrives, which is how "carry forward" works.
 */
@Service
public class MarketDataService {

    // A same-day re-observation within this tolerance is treated as a
    // duplicate/retry rather than a genuine conflict.
    private static final double CONFLICT_TOLERANCE_PCT = 0.0005;

    private final MarketDataProvider provider;
    private final MarketSnapshotRepository snapshotRepository;
    private final MarketObservationRepository observationRepository;
    private final ObjectMapper objectMapper;

    public MarketDataService(
            MarketDataProvider provider,
            MarketSnapshotRepository snapshotRepository,
            MarketObservationRepository observationRepository,
            ObjectMapper objectMapper) {
        this.provider = provider;
        this.snapshotRepository = snapshotRepository;
        this.observationRepository = observationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ensures today's (per the provider's clock) observation for a symbol is
     * reflected in the database, then returns whatever the latest known
     * snapshot is - which may be older than today if the provider had no
     * data (an outage), by design.
     */
    @Transactional
    public Optional<MarketSnapshot> ensureIngestedAndGetLatest(String symbol) {
        LocalDate today = provider.latestAvailableDate();
        ingestDay(symbol, today);
        return snapshotRepository.findTopBySymbolOrderByAsOfDesc(symbol);
    }

    /** Ensures every day in [from, to] the provider has data for is persisted, then returns the stored range. */
    @Transactional
    public List<MarketSnapshot> ensureIngestedHistory(String symbol, LocalDate from, LocalDate to) {
        for (DailyObservation obs : provider.history(symbol, from, to)) {
            ingestOne(obs);
        }
        return snapshotRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(symbol, atClose(from), atClose(to));
    }

    private void ingestDay(String symbol, LocalDate day) {
        Optional<DailyObservation> obs = provider.observationOn(symbol, day);
        if (obs.isPresent()) {
            ingestOne(obs.get());
        } else {
            recordOutage(symbol, day);
        }
    }

    private void ingestOne(DailyObservation obs) {
        Optional<MarketSnapshot> existing = snapshotRepository.findBySymbolAndAsOf(obs.symbol(), obs.sourceTimestamp());

        if (existing.isEmpty()) {
            MarketSnapshot snapshot = snapshotRepository.save(new MarketSnapshot(
                    obs.symbol(),
                    obs.sourceTimestamp(),
                    bd(obs.price()),
                    bd(obs.returnPct()),
                    obs.sector(),
                    bd(obs.sectorReturnPct()),
                    bd(obs.peerBasketReturnPct()),
                    obs.marketStatus().name(),
                    provider.name()
            ));
            recordObservation(obs, TrustStatus.LIVE, snapshot.getId(), "new snapshot");
            return;
        }

        MarketSnapshot current = existing.get();
        boolean matches = Math.abs(current.getPrice().doubleValue() - obs.price()) < CONFLICT_TOLERANCE_PCT * obs.price();

        if (matches) {
            // Same value re-arriving - a duplicate/retry. Idempotent no-op on
            // the snapshot, but still logged for provenance.
            recordObservation(obs, TrustStatus.LIVE, current.getId(), "duplicate observation, matched existing snapshot");
        } else {
            // Two different values for the same (symbol, as_of). First write
            // wins; the disagreement is recorded rather than silently
            // overwriting an already-accepted snapshot.
            recordObservation(obs, TrustStatus.CONFLICTING, current.getId(), "conflicts with previously accepted snapshot");
        }
    }

    private void recordOutage(String symbol, LocalDate day) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", "provider had no data for this symbol/day");
        payload.put("date", day.toString());
        observationRepository.save(new MarketObservation(
                symbol, atClose(day), TrustStatus.UNAVAILABLE.name(), toJson(payload), null));
    }

    private void recordObservation(DailyObservation obs, TrustStatus trustStatus, UUID snapshotId, String note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("note", note);
        payload.put("price", obs.price());
        payload.put("returnPct", obs.returnPct());
        observationRepository.save(new MarketObservation(
                obs.symbol(), obs.sourceTimestamp(), trustStatus.name(), toJson(payload), snapshotId));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static java.time.Instant atClose(LocalDate date) {
        return date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
    }
}
