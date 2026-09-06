package com.undertow.backtest.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.backtest.service.BacktestService;
import com.undertow.backtest.service.BacktestService.BacktestResult;
import com.undertow.common.exception.InvalidRequestException;

@RestController
@RequestMapping("/api/v1/backtest")
public class BacktestController {

    private final BacktestService backtestService;

    public BacktestController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/replay")
    public BacktestResult replay(@RequestParam(name = "rangeDays", defaultValue = "30") int rangeDays) {
        if (rangeDays != 7 && rangeDays != 30 && rangeDays != 90) {
            throw new InvalidRequestException("rangeDays must be 7, 30, or 90");
        }
        return backtestService.run(rangeDays);
    }
}
