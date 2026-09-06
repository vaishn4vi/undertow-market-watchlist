package com.undertow.market.dto;

import com.undertow.market.service.Symbol;

public record SymbolSearchResult(String ticker, String name, String sector) {
    public static SymbolSearchResult from(Symbol symbol) {
        return new SymbolSearchResult(symbol.ticker(), symbol.name(), symbol.sector());
    }
}
