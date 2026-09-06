package com.undertow.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.backtest.service.BacktestService;
import com.undertow.backtest.service.BacktestService.BacktestResult;

@SpringBootTest
@ActiveProfiles("test")
class BacktestServiceTest {

    @Autowired
    private BacktestService backtestService;

    @Test
    void backtestRunsWithoutErrorsAndProducesSaneAggregates() {
        BacktestResult result = backtestService.run(30);

        assertThat(result.rangeDays()).isEqualTo(30);
        assertThat(result.totalDetected()).isGreaterThanOrEqualTo(0);
        assertThat(result.totalPersisted()).isLessThanOrEqualTo(result.totalDetected());
        assertThat(result.totalPremature()).isLessThanOrEqualTo(result.totalDetected());
        assertThat(result.meanReversionRate()).isBetween(0.0, 1.0);
        assertThat(result.bySymbol()).isNotEmpty();
    }

    @Test
    void differentRangesProduceDifferentWindows() {
        BacktestResult sevenDay = backtestService.run(7);
        BacktestResult ninetyDay = backtestService.run(90);

        assertThat(sevenDay.from()).isAfter(ninetyDay.from());
    }

    @Test
    void everySymbolResultHasNonNegativeCounts() {
        BacktestResult result = backtestService.run(30);
        result.bySymbol().forEach(s -> {
            assertThat(s.detected()).isGreaterThanOrEqualTo(0);
            assertThat(s.persisted()).isGreaterThanOrEqualTo(0);
            assertThat(s.resolved()).isGreaterThanOrEqualTo(0);
            assertThat(s.prematureAlerts()).isGreaterThanOrEqualTo(0);
        });
    }
}
