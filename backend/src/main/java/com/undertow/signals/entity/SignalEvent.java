package com.undertow.signals.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signal_events")
public class SignalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private int severity;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    protected SignalEvent() {
        // JPA
    }

    public SignalEvent(String symbol, String type, int severity, BigDecimal confidence, UUID snapshotId) {
        this.symbol = symbol;
        this.type = type;
        this.severity = severity;
        this.confidence = confidence;
        this.detectedAt = Instant.now();
        this.snapshotId = snapshotId;
    }

    public UUID getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getType() {
        return type;
    }

    public int getSeverity() {
        return severity;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public UUID getSnapshotId() {
        return snapshotId;
    }

    public UUID getSupersededBy() {
        return supersededBy;
    }
}
