package com.undertow.market.service;

/**
 * A validated symbol with its sector, drawn from a fixed reference universe.
 *
 * Kept intentionally small and in-memory for the hackathon: this is the same
 * universe the DemoMarketDataProvider (Phase 3) will simulate prices for, so
 * a symbol you can add to a watchlist today is guaranteed to have signals
 * generated for it once the engine lands - no orphaned symbols.
 */
public record Symbol(String ticker, String name, String sector) {
}
