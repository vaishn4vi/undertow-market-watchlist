package com.undertow.attention.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.attention.dto.AttentionDebtResponse;
import com.undertow.attention.dto.DebtHistoryPointResponse;
import com.undertow.attention.service.AttentionDebtService;
import com.undertow.attention.service.AttentionDebtService.DebtWithEntries;
import com.undertow.attention.service.AttentionDebtService.PriorityQueueResult;
import com.undertow.config.CurrentUser;
import com.undertow.users.service.UserService;

@RestController
@RequestMapping("/api/v1/attention")
public class AttentionDebtController {

    private final AttentionDebtService debtService;
    private final UserService userService;

    public AttentionDebtController(AttentionDebtService debtService, UserService userService) {
        this.debtService = debtService;
        this.userService = userService;
    }

    @GetMapping("/debt")
    public AttentionDebtResponse debt(@CurrentUser String userId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        DebtWithEntries current = debtService.compute(internalId);
        PriorityQueueResult prioritized = debtService.prioritize(current.openEntries(), current.result().band());
        return AttentionDebtResponse.from(current.result(), prioritized.shown(), prioritized.deferredCount());
    }

    @GetMapping("/debt/history")
    public List<DebtHistoryPointResponse> debtHistory(@CurrentUser String userId) {
        UUID internalId = userService.getOrCreate(userId).getId();
        return debtService.history(internalId).stream().map(DebtHistoryPointResponse::from).toList();
    }
}
