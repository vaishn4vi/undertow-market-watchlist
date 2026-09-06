package com.undertow.trust.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.common.exception.InvalidRequestException;
import com.undertow.config.CurrentUser;
import com.undertow.market.service.SymbolDirectory;
import com.undertow.trust.dto.DataTrustOverviewResponse;
import com.undertow.trust.dto.TrustAssessmentResponse;
import com.undertow.trust.service.DataTrustOverviewService;
import com.undertow.trust.service.TrustService;

@RestController
@RequestMapping("/api/v1/trust")
public class TrustController {

    private final TrustService trustService;
    private final SymbolDirectory symbolDirectory;
    private final DataTrustOverviewService dataTrustOverviewService;

    public TrustController(
            TrustService trustService,
            SymbolDirectory symbolDirectory,
            DataTrustOverviewService dataTrustOverviewService
    ) {
        this.trustService = trustService;
        this.symbolDirectory = symbolDirectory;
        this.dataTrustOverviewService = dataTrustOverviewService;
    }

    @GetMapping("/symbols/{symbol}")
    public TrustAssessmentResponse forSymbol(@PathVariable String symbol) {
        String ticker = symbol.toUpperCase();
        symbolDirectory.find(ticker).orElseThrow(() -> new InvalidRequestException("Unknown symbol: " + ticker));

        return trustService.assess(ticker)
                .map(TrustAssessmentResponse::from)
                .orElseGet(TrustAssessmentResponse::unknown);
    }

    /**
     * Aggregate trust picture across every symbol the current user tracks,
     * for the global "market data" status indicator (see Undertow UI spec,
     * Data Trust section). Pure aggregation over existing per-symbol
     * assessments - no new trust logic.
     */
    @GetMapping("/overview")
    public DataTrustOverviewResponse overview(@CurrentUser String userId) {
        return dataTrustOverviewService.overviewForUser(userId);
    }
}
