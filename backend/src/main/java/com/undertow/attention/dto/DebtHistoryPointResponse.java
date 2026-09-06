package com.undertow.attention.dto;

import java.time.Instant;

import com.undertow.attention.entity.AttentionDebtSnapshot;

public record DebtHistoryPointResponse(
        Instant computedAt,
        double normalizedDebt,
        String band,
        String trajectory
) {
    public static DebtHistoryPointResponse from(AttentionDebtSnapshot s) {
        return new DebtHistoryPointResponse(s.getComputedAt(), s.getNormalizedDebt().doubleValue(), s.getBand(), s.getTrajectory());
    }
}
