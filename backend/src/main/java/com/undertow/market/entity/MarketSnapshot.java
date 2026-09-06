package com.undertow.market.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// The uniqueConstraint here mirrors V2__market_snapshot_uniqueness.sql,
// which adds this constraint via raw SQL for the real Postgres database
// (Flyway-managed schemas intentionally don't use Hibernate DDL). The test
// profile instead derives its H2 schema straight from these annotations
// (ddl-auto: create-drop, Flyway disabled) - without restating the
// constraint here, H2 test runs silently allow duplicate
// (symbol, as_of) rows that Postgres would reject, so ingestion's
// conflict-handling code path never actually gets exercised in tests.
@Entity
@Table(name = "market_snapshots", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "as_of"}))
public class MarketSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "return_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal returnPct;

    @Column(nullable = false)
    private String sector;

    @Column(name = "sector_return_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal sectorReturnPct;

    @Column(name = "peer_basket_return_pct", nullable = false, precision = 8, scale = 4)
    private BigDecimal peerBasketReturnPct;

    @Column(name = "market_status", nullable = false)
    private String marketStatus;

    @Column(nullable = false)
    private String provider;

    protected MarketSnapshot() {
        // JPA
    }

    public MarketSnapshot(
            String symbol, Instant asOf, BigDecimal price, BigDecimal returnPct, String sector,
            BigDecimal sectorReturnPct, BigDecimal peerBasketReturnPct, String marketStatus, String provider) {
        this.symbol = symbol;
        this.asOf = asOf;
        this.ingestedAt = Instant.now();
        this.price = price;
        this.returnPct = returnPct;
        this.sector = sector;
        this.sectorReturnPct = sectorReturnPct;
        this.peerBasketReturnPct = peerBasketReturnPct;
        this.marketStatus = marketStatus;
        this.provider = provider;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getReturnPct() {
        return returnPct;
    }

    public String getSector() {
        return sector;
    }

    public BigDecimal getSectorReturnPct() {
        return sectorReturnPct;
    }

    public BigDecimal getPeerBasketReturnPct() {
        return peerBasketReturnPct;
    }

    public String getMarketStatus() {
        return marketStatus;
    }

    public String getProvider() {
        return provider;
    }
}
