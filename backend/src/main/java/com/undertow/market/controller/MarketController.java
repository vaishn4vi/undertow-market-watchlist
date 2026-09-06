package com.undertow.market.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.common.exception.InvalidRequestException;
import com.undertow.market.dto.MarketHistoryPointResponse;
import com.undertow.market.dto.MarketSnapshotResponse;
import com.undertow.market.service.MarketDataProvider;
import com.undertow.market.service.MarketDataService;
import com.undertow.market.service.SymbolDirectory;

@RestController
@RequestMapping("/api/v1/market")
public class MarketController {

    private final MarketDataService marketDataService;
    private final MarketDataProvider provider;
    private final SymbolDirectory symbolDirectory;

    public MarketController(MarketDataService marketDataService, MarketDataProvider provider,
                             SymbolDirectory symbolDirectory) {
        this.marketDataService = marketDataService;
        this.provider = provider;
        this.symbolDirectory = symbolDirectory;
    }

    @GetMapping("/snapshots")
    public List<MarketSnapshotResponse> snapshots(@RequestParam("symbols") String symbolsCsv) {
        java.time.Instant currentAsOf = provider.latestAvailableDate()
                .atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();

        return List.of(symbolsCsv.split(",")).stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isBlank())
                .map(symbol -> {
                    requireKnownSymbol(symbol);
                    return marketDataService.ensureIngestedAndGetLatest(symbol)
                            .map(snapshot -> MarketSnapshotResponse.from(snapshot, currentAsOf))
                            .orElse(null);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @GetMapping("/symbols/{symbol}/history")
    public List<MarketHistoryPointResponse> history(
            @PathVariable String symbol,
            @RequestParam(name = "range", defaultValue = "30d") String range) {
        requireKnownSymbol(symbol.toUpperCase());

        int days = parseRangeDays(range);
        LocalDate to = provider.latestAvailableDate();
        LocalDate from = to.minusDays(days);

        return marketDataService.ensureIngestedHistory(symbol.toUpperCase(), from, to).stream()
                .map(MarketHistoryPointResponse::from)
                .toList();
    }

    private void requireKnownSymbol(String symbol) {
        symbolDirectory.find(symbol)
                .orElseThrow(() -> new InvalidRequestException("Unknown symbol: " + symbol));
    }

    private int parseRangeDays(String range) {
        return switch (range) {
            case "7d" -> 7;
            case "30d" -> 30;
            case "90d" -> 90;
            default -> throw new InvalidRequestException("Unsupported range: " + range + " (use 7d, 30d, or 90d)");
        };
    }
}
