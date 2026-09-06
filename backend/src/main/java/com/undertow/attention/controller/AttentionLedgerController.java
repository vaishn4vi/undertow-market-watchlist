package com.undertow.attention.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.attention.dto.LedgerEntryResponse;
import com.undertow.attention.service.AttentionLedgerService;
import com.undertow.common.exception.InvalidRequestException;
import com.undertow.common.exception.NotFoundException;
import com.undertow.config.CurrentUser;
import com.undertow.market.service.SymbolDirectory;
import com.undertow.users.service.UserService;

@RestController
@RequestMapping("/api/v1/attention/ledger")
public class AttentionLedgerController {

    private final AttentionLedgerService ledgerService;
    private final UserService userService;
    private final SymbolDirectory symbolDirectory;

    public AttentionLedgerController(AttentionLedgerService ledgerService, UserService userService,
                                      SymbolDirectory symbolDirectory) {
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.symbolDirectory = symbolDirectory;
    }

    @GetMapping
    public List<LedgerEntryResponse> list(@CurrentUser String userId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        return ledgerService.listForUser(internalId).stream().map(LedgerEntryResponse::from).toList();
    }

    /**
     * Manual trigger for testing/demo purposes - automatic triggering as
     * part of a user's check-in across their whole watchlist lands with
     * reconciliation in Phase 8.
     */
    @PostMapping("/sync/{symbol}")
    public List<LedgerEntryResponse> sync(@CurrentUser String userId, @PathVariable String symbol) {
        String ticker = symbol.toUpperCase();
        symbolDirectory.find(ticker).orElseThrow(() -> new InvalidRequestException("Unknown symbol: " + ticker));

        UUID internalId = userService.getOrCreate(userId).getId();
        return ledgerService.sync(internalId, ticker).stream().map(LedgerEntryResponse::from).toList();
    }

    @PostMapping("/{entryId}/acknowledge")
    public LedgerEntryResponse acknowledge(@CurrentUser String userId, @PathVariable UUID entryId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        return ledgerService.acknowledge(internalId, entryId)
                .map(LedgerEntryResponse::from)
                .orElseThrow(() -> NotFoundException.of("SignalLedgerEntry", entryId));
    }

    @PostMapping("/{entryId}/dismiss")
    public LedgerEntryResponse dismiss(@CurrentUser String userId, @PathVariable UUID entryId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        return ledgerService.dismiss(internalId, entryId)
                .map(LedgerEntryResponse::from)
                .orElseThrow(() -> NotFoundException.of("SignalLedgerEntry", entryId));
    }

    @PostMapping("/{entryId}/keep-watching")
    public LedgerEntryResponse keepWatching(@CurrentUser String userId, @PathVariable UUID entryId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        return ledgerService.keepWatching(internalId, entryId)
                .map(LedgerEntryResponse::from)
                .orElseThrow(() -> NotFoundException.of("SignalLedgerEntry", entryId));
    }
}
