package com.undertow.signals.dto;

import java.time.Instant;
import java.util.UUID;

import com.undertow.signals.entity.SignalEvent;

public record SignalEventResponse(
        UUID id,
        String symbol,
        String type,
        int severity,
        double confidence,
        Instant detectedAt
) {
    public static SignalEventResponse from(SignalEvent event) {
        return new SignalEventResponse(
                event.getId(), event.getSymbol(), event.getType(), event.getSeverity(),
                event.getConfidence().doubleValue(), event.getDetectedAt());
    }
}
