package com.undertow.market.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class SymbolDirectory {

    // A deliberately small, fixed universe grouped into sectors that actually
    // move together in the demo simulator - real decoupling/silence detection
    // needs peer groups with a genuine historical relationship to break from.
    private static final List<Symbol> UNIVERSE = List.of(
            new Symbol("BHRT", "Bharat Technologies", "Technology"),
            new Symbol("GNGS", "Ganges Software", "Technology"),
            new Symbol("HIML", "Himalaya Semiconductors", "Technology"),
            new Symbol("SAHY", "Sahyadri Systems", "Technology"),

            new Symbol("KAVR", "Kaveri Bank", "Financials"),
            new Symbol("SPTM", "Saptagiri Capital", "Financials"),
            new Symbol("VRDN", "Vardhan Financial", "Financials"),
            new Symbol("UDAY", "Uday Insurance", "Financials"),

            new Symbol("ARGY", "Arogya Pharma", "Healthcare"),
            new Symbol("NIRM", "Nirmaya Diagnostics", "Healthcare"),
            new Symbol("SVAS", "Swasthya Med", "Healthcare"),

            new Symbol("SURY", "Surya Energy", "Energy"),
            new Symbol("PVAN", "Pavan Solar Corp", "Energy"),
            new Symbol("TEJA", "Tejas Industries", "Energy"),

            new Symbol("BAZR", "Bazaar Retail", "Consumer"),
            new Symbol("GRIH", "Griha & Home", "Consumer"),
            new Symbol("YATR", "Yatra Travel", "Consumer"),

            new Symbol("UDYG", "Udyog Industrial", "Industrials"),
            new Symbol("MARG", "Marg Logistics", "Industrials"),
            new Symbol("SHIL", "Shila Materials", "Industrials")
    );

    private static final Map<String, Symbol> BY_TICKER = UNIVERSE.stream()
            .collect(Collectors.toMap(Symbol::ticker, s -> s));

    public Optional<Symbol> find(String ticker) {
        if (ticker == null) return Optional.empty();
        return Optional.ofNullable(BY_TICKER.get(ticker.trim().toUpperCase()));
    }

    public List<Symbol> search(String query) {
        if (query == null || query.isBlank()) {
            return UNIVERSE;
        }
        String q = query.trim().toUpperCase();
        return UNIVERSE.stream()
                .filter(s -> s.ticker().contains(q) || s.name().toUpperCase().contains(q))
                .toList();
    }

    public List<Symbol> peersInSector(String sector, String excludingTicker) {
        return UNIVERSE.stream()
                .filter(s -> s.sector().equalsIgnoreCase(sector) && !s.ticker().equals(excludingTicker))
                .toList();
    }
}
