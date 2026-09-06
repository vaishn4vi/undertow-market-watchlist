package com.undertow.signals.entity;

import java.math.BigDecimal;
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
@Table(name = "signal_evidence")
public class SignalEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "signal_event_id", nullable = false)
    private UUID signalEventId;

    @Column(name = "stock_return", nullable = false, precision = 8, scale = 4)
    private BigDecimal stockReturn;

    @Column(name = "sector_return", nullable = false, precision = 8, scale = 4)
    private BigDecimal sectorReturn;

    @Column(name = "expected_return", nullable = false, precision = 8, scale = 4)
    private BigDecimal expectedReturn;

    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal deviation;

    @Column(name = "historical_percentile", nullable = false, precision = 5, scale = 2)
    private BigDecimal historicalPercentile;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_extra")
    private String evidenceExtra;

    protected SignalEvidence() {
        // JPA
    }

    public SignalEvidence(UUID signalEventId, BigDecimal stockReturn, BigDecimal sectorReturn,
                           BigDecimal expectedReturn, BigDecimal deviation, BigDecimal historicalPercentile,
                           String evidenceExtra) {
        this.signalEventId = signalEventId;
        this.stockReturn = stockReturn;
        this.sectorReturn = sectorReturn;
        this.expectedReturn = expectedReturn;
        this.deviation = deviation;
        this.historicalPercentile = historicalPercentile;
        this.evidenceExtra = evidenceExtra;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSignalEventId() {
        return signalEventId;
    }

    public BigDecimal getStockReturn() {
        return stockReturn;
    }

    public BigDecimal getSectorReturn() {
        return sectorReturn;
    }

    public BigDecimal getExpectedReturn() {
        return expectedReturn;
    }

    public BigDecimal getDeviation() {
        return deviation;
    }

    public BigDecimal getHistoricalPercentile() {
        return historicalPercentile;
    }

    public String getEvidenceExtra() {
        return evidenceExtra;
    }
}
