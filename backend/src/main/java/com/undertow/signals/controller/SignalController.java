package com.undertow.signals.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.undertow.common.exception.InvalidRequestException;
import com.undertow.common.exception.NotFoundException;
import com.undertow.market.service.SymbolDirectory;
import com.undertow.signals.dto.DetectionResponse;
import com.undertow.signals.dto.SignalEvidenceResponse;
import com.undertow.signals.dto.SignalEventResponse;
import com.undertow.signals.service.SignalDetectionService;

@RestController
@RequestMapping("/api/v1/signals")
public class SignalController {

    private final SignalDetectionService signalDetectionService;
    private final SymbolDirectory symbolDirectory;
    private final ObjectMapper objectMapper;

    public SignalController(SignalDetectionService signalDetectionService, SymbolDirectory symbolDirectory,
                             ObjectMapper objectMapper) {
        this.signalDetectionService = signalDetectionService;
        this.symbolDirectory = symbolDirectory;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/symbols/{symbol}")
    public List<SignalEventResponse> forSymbol(@PathVariable String symbol) {
        requireKnownSymbol(symbol.toUpperCase());
        return signalDetectionService.latestForSymbol(symbol.toUpperCase()).stream()
                .map(SignalEventResponse::from)
                .toList();
    }

    /**
     * Manual trigger for testing/demo purposes (automatic triggering as part
     * of a user's check-in lands with reconciliation in Phase 8). Confidence
     * is derived from the symbol's real trust assessment (Phase 5) - when
     * that assessment is UNAVAILABLE, no new evaluation runs at all and the
     * response reports the existing signal history untouched, with
     * dataUnavailable=true.
     */
    @PostMapping("/symbols/{symbol}/detect")
    public DetectionResponse detect(@PathVariable String symbol) {
        requireKnownSymbol(symbol.toUpperCase());
        return DetectionResponse.from(signalDetectionService.detectForSymbol(symbol.toUpperCase()));
    }

    @GetMapping("/{signalEventId}/evidence")
    public SignalEvidenceResponse evidence(@PathVariable UUID signalEventId) {
        return signalDetectionService.evidenceFor(signalEventId)
                .map(e -> SignalEvidenceResponse.from(e, objectMapper))
                .orElseThrow(() -> NotFoundException.of("SignalEvidence", signalEventId));
    }

    private void requireKnownSymbol(String symbol) {
        symbolDirectory.find(symbol)
                .orElseThrow(() -> new InvalidRequestException("Unknown symbol: " + symbol));
    }
}
