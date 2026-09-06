package com.undertow.backtest.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.undertow.attention.service.LedgerHysteresis;
import com.undertow.attention.service.LedgerHysteresis.EntryState;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.market.service.DailyObservation;
import com.undertow.market.service.ReplayMarketDataProvider;
import com.undertow.market.service.Symbol;
import com.undertow.market.service.SymbolDirectory;
import com.undertow.signals.model.SignalType;
import com.undertow.signals.service.HistoricalReturn;
import com.undertow.signals.service.SignalCandidate;
import com.undertow.signals.service.SignalEngine;

/**
 * Validates that the persist/resolve hysteresis behaves sensibly on data
 * that was never engineered to contain a signal - i.e. checks the
 * false-positive/premature-alert rate on pure noise. Entirely in-memory:
 * runs the real SignalEngine and the real LedgerHysteresis pure logic
 * against ReplayMarketDataProvider's baseline series, but never touches the
 * database. Not investment advice or prediction - purely an engineering
 * validation tool for the detection thresholds themselves.
 */
@Service
public class BacktestService {

    private static final int PERSIST_THRESHOLD = 70;
    private static final int RESOLVE_THRESHOLD = 50;
    private static final int WORSEN_DELTA = 15;
    private static final int LOOKBACK_BUFFER_DAYS = 30; // extra history before the window so day 1 has enough context

    private final ReplayMarketDataProvider replayProvider;
    private final SymbolDirectory symbolDirectory;
    private final SignalEngine engine = new SignalEngine();

    public BacktestService(ReplayMarketDataProvider replayProvider, SymbolDirectory symbolDirectory) {
        this.replayProvider = replayProvider;
        this.symbolDirectory = symbolDirectory;
    }

    public record SymbolResult(
            String symbol, int detected, int persisted, int resolved,
            int prematureAlerts, double meanReversionRate, double averageLifetimeDays) {
    }

    public record BacktestResult(
            int rangeDays, LocalDate from, LocalDate to,
            int totalDetected, int totalPersisted, int totalResolved, int totalPremature,
            double meanReversionRate, double averageSignalLifetimeDays,
            List<SymbolResult> bySymbol
    ) {
    }

    public BacktestResult run(int rangeDays) {
        LocalDate to = replayProvider.latestAvailableDate();
        LocalDate from = to.minusDays(rangeDays);
        LocalDate fetchFrom = from.minusDays(LOOKBACK_BUFFER_DAYS);

        List<SymbolResult> results = new ArrayList<>();
        for (Symbol symbol : symbolDirectory.search(null)) {
            results.add(runForSymbol(symbol.ticker(), fetchFrom, from, to));
        }

        int totalDetected = results.stream().mapToInt(SymbolResult::detected).sum();
        int totalPersisted = results.stream().mapToInt(SymbolResult::persisted).sum();
        int totalResolved = results.stream().mapToInt(SymbolResult::resolved).sum();
        int totalPremature = results.stream().mapToInt(SymbolResult::prematureAlerts).sum();
        double meanReversionRate = totalDetected == 0 ? 0.0 : (double) totalPremature / totalDetected;
        double avgLifetime = results.stream()
                .filter(r -> r.resolved() > 0)
                .mapToDouble(SymbolResult::averageLifetimeDays)
                .average().orElse(0.0);

        return new BacktestResult(rangeDays, from, to, totalDetected, totalPersisted, totalResolved,
                totalPremature, round(meanReversionRate), round(avgLifetime), results);
    }

    private SymbolResult runForSymbol(String symbol, LocalDate fetchFrom, LocalDate windowStart, LocalDate windowEnd) {
        List<DailyObservation> fullSeries = replayProvider.history(symbol, fetchFrom, windowEnd);

        List<HistoricalReturn> priorHistory = new ArrayList<>();
        Map<SignalType, EntryState> openEntries = new HashMap<>();
        Map<SignalType, LocalDate> creationDates = new HashMap<>();
        Map<SignalType, Boolean> everPersisted = new HashMap<>();

        int detected = 0, persisted = 0, resolved = 0, premature = 0;
        List<Double> lifetimes = new ArrayList<>();

        for (DailyObservation obs : fullSeries) {
            HistoricalReturn today = new HistoricalReturn(obs.date(), obs.returnPct(), obs.sectorReturnPct());

            if (!obs.date().isBefore(windowStart)) {
                List<SignalCandidate> shadows = engine.evaluateAll(priorHistory, today, 1.0);
                for (SignalCandidate shadow : shadows) {
                    SignalType type = shadow.type();
                    EntryState current = openEntries.get(type);

                    if (current == null) {
                        if (shadow.triggered()) {
                            EntryState created = LedgerHysteresis.create(shadow.severity(), PERSIST_THRESHOLD, RESOLVE_THRESHOLD);
                            openEntries.put(type, created);
                            creationDates.put(type, obs.date());
                            everPersisted.put(type, created.status() == LedgerStatus.PERSISTED);
                            detected++;
                        }
                        continue;
                    }

                    EntryState next = LedgerHysteresis.update(current, shadow.severity(), PERSIST_THRESHOLD, RESOLVE_THRESHOLD, WORSEN_DELTA);
                    if (next.status() == LedgerStatus.PERSISTED && current.status() != LedgerStatus.PERSISTED) {
                        persisted++;
                        everPersisted.put(type, true);
                    }
                    if (next.status() == LedgerStatus.RESOLVED) {
                        resolved++;
                        double lifetimeDays = java.time.temporal.ChronoUnit.DAYS.between(creationDates.get(type), obs.date());
                        lifetimes.add(lifetimeDays);
                        if (!Boolean.TRUE.equals(everPersisted.get(type))) {
                            premature++;
                        }
                        openEntries.remove(type);
                        creationDates.remove(type);
                        everPersisted.remove(type);
                        continue;
                    }
                    openEntries.put(type, next);
                }
            }

            priorHistory.add(today);
        }

        double meanReversionRate = detected == 0 ? 0.0 : (double) premature / detected;
        double avgLifetime = lifetimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return new SymbolResult(symbol, detected, persisted, resolved, premature, round(meanReversionRate), round(avgLifetime));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
