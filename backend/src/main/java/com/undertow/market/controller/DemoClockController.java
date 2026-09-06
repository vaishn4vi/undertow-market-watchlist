package com.undertow.market.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.market.service.DemoMarketDataProvider;

/**
 * Demo-only clock controls. Kept off the generic MarketDataProvider interface
 * deliberately - "advancing time" is a demo/testing concept, not something a
 * real or replay data source has any business exposing.
 *
 * This bean only exists when undertow.market.provider=demo (the default), so
 * these endpoints simply 404 (no matching bean to autowire) under any other
 * provider configuration - Spring will fail to start the controller in that
 * case, which is desired: there is nothing meaningful for it to control.
 */
@RestController
@RequestMapping("/api/v1/market/demo")
public class DemoClockController {

    private final DemoMarketDataProvider demoProvider;

    public DemoClockController(DemoMarketDataProvider demoProvider) {
        this.demoProvider = demoProvider;
    }

    @PostMapping("/reset")
    public Map<String, LocalDate> reset() {
        return Map.of("clock", demoProvider.resetToStart());
    }

    @PostMapping("/advance")
    public Map<String, LocalDate> advance(@RequestParam(name = "days", defaultValue = "1") int days) {
        return Map.of("clock", demoProvider.advance(days));
    }

    @PostMapping("/fast-forward")
    public Map<String, LocalDate> fastForward() {
        return Map.of("clock", demoProvider.fastForwardToReturnDay());
    }
}
