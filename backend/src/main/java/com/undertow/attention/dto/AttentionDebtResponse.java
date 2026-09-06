package com.undertow.attention.dto;

import java.util.List;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.service.AttentionDebtEngine.DebtResult;

public record AttentionDebtResponse(
        double rawDebt,
        double normalizedDebt,
        String band,
        String trajectory,
        String explanation,
        List<LedgerEntryResponse> topPriorities,
        int deferredCount
) {
    public static AttentionDebtResponse from(DebtResult result, List<SignalLedgerEntry> shown, int deferredCount) {
        String explanation = switch (result.trajectory()) {
            case DIVERGING -> "New unresolved signals are arriving faster than they are resolving.";
            case CONVERGING -> "Unresolved signals are clearing faster than new ones are appearing.";
            case STABLE -> "Unresolved signals are roughly holding steady.";
        };
        return new AttentionDebtResponse(
                round(result.rawDebt()), round(result.normalizedDebt()), result.band().name(),
                result.trajectory().name(), explanation,
                shown.stream().map(LedgerEntryResponse::from).toList(), deferredCount);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
