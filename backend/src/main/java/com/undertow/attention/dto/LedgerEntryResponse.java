package com.undertow.attention.dto;

import java.time.Instant;
import java.util.UUID;

import com.undertow.attention.entity.SignalLedgerEntry;

public record LedgerEntryResponse(
        UUID id,
        String symbol,
        String signalType,
        String status,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        Integer previousSeverity,
        int currentSeverity,
        int maxSeverity,
        Instant resolvedAt,
        String verificationStatus,
        int persistenceCount,
        int resolveStreak,
        boolean worsenedFlag,
        boolean acknowledged,
        Instant acknowledgedAt,
        UUID latestSignalEventId,
        boolean dismissed
) {
    public static LedgerEntryResponse from(SignalLedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(), e.getSymbol(), e.getSignalType(), e.getStatus(),
                e.getFirstDetectedAt(), e.getLastDetectedAt(), e.getPreviousSeverity(),
                e.getCurrentSeverity(), e.getMaxSeverity(), e.getResolvedAt(), e.getVerificationStatus(),
                e.getPersistenceCount(), e.getResolveStreak(), e.isWorsenedFlag(),
                e.isAcknowledged(), e.getAcknowledgedAt(), e.getLatestSignalEventId(), e.isDismissed()
        );
    }
}
