package com.undertow.signals.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undertow.signals.entity.SignalEvidence;

public record SignalEvidenceResponse(
        BigDecimal stockReturn,
        BigDecimal sectorReturn,
        BigDecimal expectedReturn,
        BigDecimal deviation,
        BigDecimal historicalPercentile,
        Map<String, Object> extra
) {
    public static SignalEvidenceResponse from(SignalEvidence evidence, ObjectMapper objectMapper) {
        Map<String, Object> extra;
        try {
            extra = objectMapper.readValue(evidence.getEvidenceExtra(), Map.class);
        } catch (Exception e) {
            extra = Map.of();
        }
        return new SignalEvidenceResponse(
                evidence.getStockReturn(), evidence.getSectorReturn(), evidence.getExpectedReturn(),
                evidence.getDeviation(), evidence.getHistoricalPercentile(), extra);
    }
}
