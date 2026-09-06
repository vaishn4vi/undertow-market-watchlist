package com.undertow.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

import com.undertow.trust.model.TrustStatus;

/**
 * Generates a fully deterministic daily return/price series for the whole
 * symbol universe over a fixed window, optionally with a scripted anomaly
 * scenario overlaid on top of an otherwise-ordinary random walk.
 *
 * Determinism contract: the same (seed, universe, window, scenario flag)
 * always produces byte-identical output, because every random draw is seeded
 * from a hash of (seed, date, sector-or-symbol) rather than from a mutable
 * shared Random advanced sequentially - so slicing any sub-range of an
 * already-generated series never changes a value that was already computed.
 *
 * This class does not know about Spring, HTTP, or persistence - it is pure
 * simulation math, which is what makes it independently unit-testable and
 * shareable between DemoMarketDataProvider and ReplayMarketDataProvider.
 */
public class DeterministicMarketSimulator {

    private static final double SECTOR_DAILY_STDEV_PCT = 0.9;
    private static final double IDIOSYNCRATIC_DAILY_STDEV_PCT = 0.45;
    private static final double MIN_BETA = 0.7;
    private static final double MAX_BETA = 1.3;
    private static final double MIN_START_PRICE = 24.0;
    private static final double MAX_START_PRICE = 340.0;

    private final long seed;
    private final List<Symbol> universe;
    private final Map<String, List<Symbol>> peersBySector;
    private final Map<String, Double> betaBySymbol;
    private final Map<String, Double> startPriceBySymbol;
    private final ScenarioScript scenario; // null = pure baseline, no scripted anomalies

    // symbol -> (date -> generated day). TreeMap keeps dates ordered for
    // carry-forward price lookups across outage gaps.
    private final Map<String, NavigableMap<LocalDate, GeneratedDay>> series = new LinkedHashMap<>();

    public DeterministicMarketSimulator(
            long seed, List<Symbol> universe, LocalDate from, LocalDate to, ScenarioScript scenario) {
        this.seed = seed;
        this.universe = universe;
        this.scenario = scenario;
        this.peersBySector = groupBySector(universe);
        this.betaBySymbol = assignBetas(universe, seed);
        this.startPriceBySymbol = assignStartPrices(universe, seed);
        generate(from, to);
    }

    public List<DailyObservation> history(String symbol, LocalDate from, LocalDate to) {
        NavigableMap<LocalDate, GeneratedDay> bySymbol = series.get(symbol);
        if (bySymbol == null) {
            return List.of();
        }
        List<DailyObservation> result = new ArrayList<>();
        for (Map.Entry<LocalDate, GeneratedDay> entry : bySymbol.subMap(from, true, to, true).entrySet()) {
            result.add(toObservation(symbol, entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    private void generate(LocalDate from, LocalDate to) {
        for (Symbol s : universe) {
            series.put(s.ticker(), new TreeMap<>());
        }

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            // Pass 1: sector-level shock for the day.
            Map<String, Double> sectorShock = new LinkedHashMap<>();
            for (String sector : peersBySector.keySet()) {
                sectorShock.put(sector, drawSectorShock(day, sector));
            }

            // Pass 2: per-symbol return (beta * sector shock + idiosyncratic
            // noise, or a scripted override), skipping symbols with a
            // scripted outage on this day entirely.
            Map<String, Double> todaysReturn = new LinkedHashMap<>();
            for (Symbol s : universe) {
                if (scenario != null && scenario.isOutage(s.ticker(), day)) {
                    continue; // no observation at all this day - simulates a feed outage
                }
                double raw = betaBySymbol.get(s.ticker()) * sectorShock.get(s.sector())
                        + drawIdiosyncraticNoise(day, s.ticker());

                Double override = scenario != null ? scenario.overrideReturn(s.ticker(), day) : null;
                double finalReturn = override != null ? override : raw;
                todaysReturn.put(s.ticker(), finalReturn);
            }

            // Pass 3: peer basket return = mean of every other symbol's
            // actual return in the same sector today (excludes outages,
            // since they never entered todaysReturn).
            for (Symbol s : universe) {
                if (!todaysReturn.containsKey(s.ticker())) {
                    continue;
                }
                double peerBasket = peersBySector.get(s.sector()).stream()
                        .map(Symbol::ticker)
                        .filter(t -> !t.equals(s.ticker()) && todaysReturn.containsKey(t))
                        .mapToDouble(todaysReturn::get)
                        .average()
                        .orElse(sectorShock.get(s.sector()));

                double thisReturn = todaysReturn.get(s.ticker());
                double previousPrice = lastKnownPrice(s.ticker(), day);
                double price = previousPrice * (1 + thisReturn / 100.0);

                series.get(s.ticker()).put(day, new GeneratedDay(
                        price, thisReturn, sectorShock.get(s.sector()), peerBasket));
            }
        }
    }

    private double lastKnownPrice(String symbol, LocalDate beforeOrOn) {
        NavigableMap<LocalDate, GeneratedDay> bySymbol = series.get(symbol);
        Map.Entry<LocalDate, GeneratedDay> priorEntry = bySymbol.floorEntry(beforeOrOn.minusDays(1));
        return priorEntry != null ? priorEntry.getValue().price() : startPriceBySymbol.get(symbol);
    }

    private double drawSectorShock(LocalDate day, String sector) {
        if (scenario != null) {
            Double override = scenario.overrideSectorShock(sector, day);
            if (override != null) return override;
        }
        Random rnd = seededRandom(seed, day, sector.hashCode());
        return rnd.nextGaussian() * SECTOR_DAILY_STDEV_PCT;
    }

    private double drawIdiosyncraticNoise(LocalDate day, String symbol) {
        Random rnd = seededRandom(seed, day, symbol.hashCode() * 0x9E3779B1);
        double noise = rnd.nextGaussian() * IDIOSYNCRATIC_DAILY_STDEV_PCT;
        if (scenario != null) {
            noise += scenario.driftBias(symbol, day);
        }
        return noise;
    }

    private static Random seededRandom(long seed, LocalDate day, int salt) {
        long mixed = seed ^ (day.toEpochDay() * 0x2545F4914F6CDD1DL) ^ salt;
        return new Random(mixed);
    }

    private static Map<String, List<Symbol>> groupBySector(List<Symbol> universe) {
        Map<String, List<Symbol>> map = new LinkedHashMap<>();
        for (Symbol s : universe) {
            map.computeIfAbsent(s.sector(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    private static Map<String, Double> assignBetas(List<Symbol> universe, long seed) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Symbol s : universe) {
            Random rnd = seededRandom(seed, LocalDate.EPOCH, s.ticker().hashCode());
            map.put(s.ticker(), MIN_BETA + rnd.nextDouble() * (MAX_BETA - MIN_BETA));
        }
        return map;
    }

    private static Map<String, Double> assignStartPrices(List<Symbol> universe, long seed) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (Symbol s : universe) {
            Random rnd = seededRandom(seed, LocalDate.EPOCH.plusDays(1), s.ticker().hashCode());
            map.put(s.ticker(), MIN_START_PRICE + rnd.nextDouble() * (MAX_START_PRICE - MIN_START_PRICE));
        }
        return map;
    }

    private DailyObservation toObservation(String symbol, LocalDate date, GeneratedDay day) {
        Symbol symbolInfo = universe.stream().filter(s -> s.ticker().equals(symbol)).findFirst().orElseThrow();
        Instant closeInstant = date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        return new DailyObservation(
                date,
                symbol,
                symbolInfo.sector(),
                round(day.price()),
                round(day.returnPct()),
                round(day.sectorShockPct()),
                round(day.peerBasketReturnPct()),
                MarketStatus.CLOSED,
                closeInstant,
                TrustStatus.LIVE
        );
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private record GeneratedDay(double price, double returnPct, double sectorShockPct, double peerBasketReturnPct) {
    }
}
