package com.undertow.reconciliation.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.config.CurrentUser;
import com.undertow.reconciliation.dto.CheckinRequest;
import com.undertow.reconciliation.dto.ReconciliationResponse;
import com.undertow.reconciliation.service.ReconciliationService;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/checkin")
    public ReconciliationResponse checkin(@CurrentUser String userId, @Valid @RequestBody CheckinRequest request) {
        return ReconciliationResponse.from(reconciliationService.checkIn(userId, request.requestId()));
    }
}
