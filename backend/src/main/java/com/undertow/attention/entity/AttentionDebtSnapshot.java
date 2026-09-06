package com.undertow.attention.entity;

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
@Table(name = "attention_debt_snapshots")
public class AttentionDebtSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @Column(name = "raw_debt", nullable = false, precision = 10, scale = 4)
    private BigDecimal rawDebt;

    @Column(name = "normalized_debt", nullable = false, precision = 5, scale = 2)
    private BigDecimal normalizedDebt;

    @Column(nullable = false)
    private String band;

    @Column(nullable = false)
    private String trajectory;

    @Column(name = "new_signal_component", nullable = false, precision = 10, scale = 4)
    private BigDecimal newSignalComponent;

    @Column(name = "worsened_component", nullable = false, precision = 10, scale = 4)
    private BigDecimal worsenedComponent;

    @Column(name = "resolved_component", nullable = false, precision = 10, scale = 4)
    private BigDecimal resolvedComponent;

    protected AttentionDebtSnapshot() {
        // JPA
    }

    public AttentionDebtSnapshot(UUID userId, BigDecimal rawDebt, BigDecimal normalizedDebt, String band,
                                  String trajectory, BigDecimal newSignalComponent, BigDecimal worsenedComponent,
                                  BigDecimal resolvedComponent) {
        this.userId = userId;
        this.computedAt = Instant.now();
        this.rawDebt = rawDebt;
        this.normalizedDebt = normalizedDebt;
        this.band = band;
        this.trajectory = trajectory;
        this.newSignalComponent = newSignalComponent;
        this.worsenedComponent = worsenedComponent;
        this.resolvedComponent = resolvedComponent;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public BigDecimal getRawDebt() {
        return rawDebt;
    }

    public BigDecimal getNormalizedDebt() {
        return normalizedDebt;
    }

    public String getBand() {
        return band;
    }

    public String getTrajectory() {
        return trajectory;
    }

    public BigDecimal getNewSignalComponent() {
        return newSignalComponent;
    }

    public BigDecimal getWorsenedComponent() {
        return worsenedComponent;
    }

    public BigDecimal getResolvedComponent() {
        return resolvedComponent;
    }
}
