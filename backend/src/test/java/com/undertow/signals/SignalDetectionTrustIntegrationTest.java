package com.undertow.signals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;
import com.undertow.signals.entity.SignalEvent;
import com.undertow.signals.repository.SignalEventRepository;
import com.undertow.signals.service.DetectionOutcome;
import com.undertow.signals.service.SignalDetectionService;

/**
 * Both tests here deliberately use HackathonDemoScenario.OUTAGE_SYMBOL
 * (ARGY) rather than BHRT/GNGS, and rely on the real scripted outage day
 * (demoProvider.fastForwardToReturnDay()) rather than manually inserting a
 * fake UNAVAILABLE MarketObservation.
 *
 * That manual-insert technique doesn't survive contact with
 * SignalDetectionService.detectForSymbol()'s own first step
 * (ensureHistoryIsIngested), which always re-attempts ingestion before
 * checking trust - correct, documented production behavior (a real system
 * should always try for fresh data first). For any symbol the provider can
 * still genuinely supply data for (BHRT and GNGS always can - only ARGY has
 * a scripted outage), that re-attempt immediately "heals" a merely-faked
 * stale observation by recording a fresh LIVE one before trust is ever
 * assessed. Using the real outage mechanism instead means there's nothing
 * to fight against.
 */
@SpringBootTest
@ActiveProfiles("test")
class SignalDetectionTrustIntegrationTest {

    @Autowired
    private SignalDetectionService signalDetectionService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private SignalEventRepository signalEventRepository;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Test
    void unavailableDataNeverChangesAnExistingSignalsSeverity() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.OUTAGE_SYMBOL;

        // Establish real prior history up to (not including) the outage
        // day, so there's a legitimate snapshot to attach a signal event to.
        List<MarketSnapshot> history = marketDataService.ensureIngestedHistory(
                symbol, demoProvider.rallyDay(), demoProvider.returnDay().minusDays(1));
        MarketSnapshot priorSnapshot = history.get(history.size() - 1);

        DetectionOutcome initial = signalDetectionService.detectForSymbol(symbol);
        assertThat(initial.dataUnavailable()).isFalse();

        // ARGY has no scripted signal of its own in the demo story (only
        // BHRT/GNGS get one) - this test is about the outage-handling
        // guarantee, not about triggering detection, so create one
        // directly to check severity-preservation against.
        SignalEvent existing = signalEventRepository.save(
                new SignalEvent(symbol, "DECOUPLING", 75, BigDecimal.valueOf(1.0), priorSnapshot.getId()));

        demoProvider.fastForwardToReturnDay(); // the real scripted outage day for ARGY

        DetectionOutcome afterOutage = signalDetectionService.detectForSymbol(symbol);

        assertThat(afterOutage.dataUnavailable()).isTrue();
        SignalEvent stillThere = afterOutage.events().stream()
                .filter(e -> e.getId().equals(existing.getId())).findFirst().orElseThrow();
        assertThat(stillThere.getSeverity()).isEqualTo(75); // not silently resolved
        assertThat(stillThere.getSeverity()).isNotEqualTo(0); // not silently escalated to/from zero either
    }

    @Test
    void repeatedUnavailableChecksRemainStableAndNeverCreateNewEvents() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.OUTAGE_SYMBOL;
        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay(), demoProvider.returnDay().minusDays(1));
        DetectionOutcome initial = signalDetectionService.detectForSymbol(symbol);

        demoProvider.fastForwardToReturnDay(); // the real scripted outage day for ARGY

        DetectionOutcome first = signalDetectionService.detectForSymbol(symbol);
        DetectionOutcome second = signalDetectionService.detectForSymbol(symbol);

        assertThat(first.dataUnavailable()).isTrue();
        assertThat(second.dataUnavailable()).isTrue();
        assertThat(first.events()).hasSameSizeAs(initial.events());
        assertThat(second.events()).hasSameSizeAs(initial.events());
    }
}
