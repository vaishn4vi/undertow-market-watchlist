package com.undertow.reconciliation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.undertow.reconciliation.service.ReconciliationService.ReconciliationEvent;
import com.undertow.reconciliation.service.ReconciliationService.ReconciliationResult;

public record ReconciliationResponse(
        UUID checkinId,
        BigDecimal daysAway,
        Instant previousCheckinAt,
        int totalMeaningfulChanges,
        int resolvedCount,
        int activeCount,
        int worsenedCount,
        int unverifiableCount,
        int newCount,
        List<ReconciliationEvent> events,
        double debtBefore,
        double debtAfter,
        String trajectory
) {
    public static ReconciliationResponse from(ReconciliationResult r) {
        int total = r.resolvedCount() + r.activeCount() + r.worsenedCount() + r.unverifiableCount() + r.newCount();
        return new ReconciliationResponse(
                r.checkinId(), r.daysAway(), r.previousCheckinAt(), total,
                r.resolvedCount(), r.activeCount(), r.worsenedCount(), r.unverifiableCount(), r.newCount(),
                r.events(), r.debtBefore(), r.debtAfter(), r.trajectory());
    }
}
