package com.undertow.attention.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signal_ledger_entries")
public class SignalLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(nullable = false)
    private String status;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "previous_severity")
    private Integer previousSeverity;

    @Column(name = "current_severity", nullable = false)
    private int currentSeverity;

    @Column(name = "max_severity", nullable = false)
    private int maxSeverity;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;

    @Column(name = "persistence_count", nullable = false)
    private int persistenceCount;

    @Column(name = "resolve_streak", nullable = false)
    private int resolveStreak;

    @Column(name = "worsened_flag", nullable = false)
    private boolean worsenedFlag;

    @Column(nullable = false)
    private boolean acknowledged;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "latest_signal_event_id")
    private UUID latestSignalEventId;

    @Column(nullable = false)
    private boolean dismissed;

    @Column(name = "last_verified_as_of")
    private Instant lastVerifiedAsOf;

    protected SignalLedgerEntry() {
        // JPA
    }

    public SignalLedgerEntry(UUID userId, String symbol, String signalType) {
        this.userId = userId;
        this.symbol = symbol;
        this.signalType = signalType;
        Instant now = Instant.now();
        this.firstDetectedAt = now;
        this.lastDetectedAt = now;
        this.persistenceCount = 0;
        this.resolveStreak = 0;
        this.worsenedFlag = false;
        this.acknowledged = false;
        this.dismissed = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getSignalType() {
        return signalType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getFirstDetectedAt() {
        return firstDetectedAt;
    }

    public Instant getLastDetectedAt() {
        return lastDetectedAt;
    }

    public void setLastDetectedAt(Instant lastDetectedAt) {
        this.lastDetectedAt = lastDetectedAt;
    }

    public Integer getPreviousSeverity() {
        return previousSeverity;
    }

    public void setPreviousSeverity(Integer previousSeverity) {
        this.previousSeverity = previousSeverity;
    }

    public int getCurrentSeverity() {
        return currentSeverity;
    }

    public void setCurrentSeverity(int currentSeverity) {
        this.currentSeverity = currentSeverity;
    }

    public int getMaxSeverity() {
        return maxSeverity;
    }

    public void setMaxSeverity(int maxSeverity) {
        this.maxSeverity = maxSeverity;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public int getPersistenceCount() {
        return persistenceCount;
    }

    public void setPersistenceCount(int persistenceCount) {
        this.persistenceCount = persistenceCount;
    }

    public int getResolveStreak() {
        return resolveStreak;
    }

    public void setResolveStreak(int resolveStreak) {
        this.resolveStreak = resolveStreak;
    }

    public boolean isWorsenedFlag() {
        return worsenedFlag;
    }

    public void setWorsenedFlag(boolean worsenedFlag) {
        this.worsenedFlag = worsenedFlag;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public UUID getLatestSignalEventId() {
        return latestSignalEventId;
    }

    public void setLatestSignalEventId(UUID latestSignalEventId) {
        this.latestSignalEventId = latestSignalEventId;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public Instant getLastVerifiedAsOf() {
        return lastVerifiedAsOf;
    }

    public void setLastVerifiedAsOf(Instant lastVerifiedAsOf) {
        this.lastVerifiedAsOf = lastVerifiedAsOf;
    }
}
