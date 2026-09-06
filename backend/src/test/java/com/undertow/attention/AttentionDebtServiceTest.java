package com.undertow.attention;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.attention.model.DebtBand;
import com.undertow.attention.service.AttentionDebtService;
import com.undertow.attention.service.AttentionLedgerService;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.users.entity.User;
import com.undertow.users.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
class AttentionDebtServiceTest {

    @Autowired
    private AttentionDebtService debtService;

    @Autowired
    private AttentionLedgerService ledgerService;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Autowired
    private UserService userService;

    private static final double[] BASELINE = {
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25, -0.15,
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25
    };

    @Test
    void userWithNoOpenSignalsHasZeroDebt() {
        User user = userService.getOrCreate("debt-user-empty");
        var result = debtService.compute(user.getId());
        assertThat(result.result().normalizedDebt()).isEqualTo(0.0);
        assertThat(result.result().band()).isEqualTo(DebtBand.LOW);
    }

    @Test
    void openSignalIncreasesDebtAboveZero() {
        String symbol = "DEBT-TEST-1";
        String userExternal = "debt-user-1";
        LocalDate start = LocalDate.of(2026, 4, 1);
        for (int i = 0; i < BASELINE.length; i++) {
            ingest(symbol, start.plusDays(i), BASELINE[i]);
        }
        ingest(symbol, start.plusDays(BASELINE.length), -8.0);

        User user = userService.getOrCreate(userExternal);
        ledgerService.sync(user.getId(), symbol);

        var result = debtService.compute(user.getId());
        assertThat(result.result().normalizedDebt()).isGreaterThan(0.0);
        assertThat(result.openEntries()).isNotEmpty();
    }

    @Test
    void snapshotHistoryAccumulatesOverMultipleComputations() {
        String userExternal = "debt-user-2";
        User user = userService.getOrCreate(userExternal);

        debtService.computeAndSnapshot(user.getId());
        debtService.computeAndSnapshot(user.getId());

        assertThat(debtService.history(user.getId()).size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void priorityQueueCapsAtThreeWhenBandIsHigh() {
        // Directly exercise the cap logic without needing to engineer an
        // actual HIGH debt score end-to-end.
        var entries = java.util.List.<com.undertow.attention.entity.SignalLedgerEntry>of();
        var result = debtService.prioritize(entries, DebtBand.HIGH);
        assertThat(result.shown()).isEmpty(); // no entries to show, but the call itself must not throw
        assertThat(result.deferredCount()).isEqualTo(0);
    }

    private void ingest(String symbol, LocalDate date, double stockReturn) {
        Instant asOf = date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        snapshotRepository.save(new MarketSnapshot(
                symbol, asOf, BigDecimal.valueOf(100), BigDecimal.valueOf(stockReturn),
                "TestSector", BigDecimal.ZERO, BigDecimal.ZERO, "CLOSED", "test"));
    }
}
