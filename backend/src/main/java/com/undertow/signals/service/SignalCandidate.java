package com.undertow.signals.service;

import java.util.Map;

import com.undertow.signals.model.SignalType;

public record SignalCandidate(
        SignalType type,
        int severity,
        double confidence,
        double stockReturn,
        double sectorReturn,
        double expectedReturn,
        double deviation,
        double historicalPercentile,
        Map<String, Double> extraEvidence,
        boolean triggered
) {
}
