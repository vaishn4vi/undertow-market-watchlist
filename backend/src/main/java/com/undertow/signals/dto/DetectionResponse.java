package com.undertow.signals.dto;

import java.util.List;

import com.undertow.signals.service.DetectionOutcome;

public record DetectionResponse(
        List<SignalEventResponse> events,
        boolean dataUnavailable,
        String trustStatus,
        double confidence
) {
    public static DetectionResponse from(DetectionOutcome outcome) {
        return new DetectionResponse(
                outcome.events().stream().map(SignalEventResponse::from).toList(),
                outcome.dataUnavailable(),
                outcome.trustStatus().name(),
                outcome.confidence()
        );
    }
}
