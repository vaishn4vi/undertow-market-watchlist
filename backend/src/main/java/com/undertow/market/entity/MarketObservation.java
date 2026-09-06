package com.undertow.market.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "market_observations")
public class MarketObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "source_timestamp", nullable = false)
    private Instant sourceTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "trust_status", nullable = false)
    private String trustStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "snapshot_id")
    private UUID snapshotId;

    protected MarketObservation() {
        // JPA
    }

    public MarketObservation(String symbol, Instant sourceTimestamp, String trustStatus,
                              String rawPayload, UUID snapshotId) {
        this.symbol = symbol;
        this.sourceTimestamp = sourceTimestamp;
        this.receivedAt = Instant.now();
        this.trustStatus = trustStatus;
        this.rawPayload = rawPayload;
        this.snapshotId = snapshotId;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public Instant getSourceTimestamp() {
        return sourceTimestamp;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getTrustStatus() {
        return trustStatus;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }
}
