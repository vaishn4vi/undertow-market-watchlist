package com.undertow.attention;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.service.AttentionLedgerService;
import com.undertow.attention.service.PersonalizationService;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.signals.model.SignalType;
import com.undertow.users.entity.User;
import com.undertow.users.entity.UserPreferences;
import com.undertow.users.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
class PersonalizationServiceTest {

    @Autowired
    private PersonalizationService personalizationService;

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
    void newUserHasDefaultThresholdWithNoAdjustment() {
        User user = userService.getOrCreate("personalization-user-1");
        int effective = personalizationService.effectivePersistThreshold(user.getId(), SignalType.DECOUPLING, 70);
        assertThat(effective).isEqualTo(70);
    }

    @Test
    void frequentDismissalsRaiseTheEffectiveThreshold() {
        User user = userService.getOrCreate("personalization-user-2");

        // Create and immediately dismiss several DECOUPLING entries across
        // different symbols to build up a dismiss-heavy history.
        for (int i = 0; i < 4; i++) {
            String symbol = "PERSONALIZE-" + i;
            LocalDate start = LocalDate.of(2026, 6, 1).plusDays(i * 30L);
            for (int d = 0; d < BASELINE.length; d++) {
                ingest(symbol, start.plusDays(d), BASELINE[d]);
            }
            ingest(symbol, start.plusDays(BASELINE.length), -8.0);

            List<SignalLedgerEntry> synced = ledgerService.sync(user.getId(), symbol);
            SignalLedgerEntry entry = synced.stream()
                    .filter(e -> e.getSignalType().equals(SignalType.DECOUPLING.name()))
                    .findFirst().orElseThrow();
            ledgerService.dismiss(user.getId(), entry.getId());
        }

        UserPreferences prefs = personalizationService.getOrCreate(user.getId());
        assertThat(prefs.getDecouplingThresholdDelta()).isGreaterThan(0);

        int effective = personalizationService.effectivePersistThreshold(user.getId(), SignalType.DECOUPLING, 70);
        assertThat(effective).isGreaterThan(70);
    }

    private void ingest(String symbol, LocalDate date, double stockReturn) {
        Instant asOf = date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        snapshotRepository.save(new MarketSnapshot(
                symbol, asOf, BigDecimal.valueOf(100), BigDecimal.valueOf(stockReturn),
                "TestSector", BigDecimal.ZERO, BigDecimal.ZERO, "CLOSED", "test"));
    }
}
