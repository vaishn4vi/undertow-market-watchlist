package com.undertow.signals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;
import com.undertow.signals.entity.SignalEvent;
import com.undertow.signals.entity.SignalEvidence;
import com.undertow.signals.service.DetectionOutcome;
import com.undertow.signals.service.SignalDetectionService;

@SpringBootTest
@ActiveProfiles("test")
class SignalDetectionServiceTest {

    @Autowired
    private SignalDetectionService signalDetectionService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Test
    void rallyDayProducesDecouplingForTheScriptedDecoupler() {
        demoProvider.resetToStart(); // clock sits at rally day
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;

        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());
        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        assertThat(outcome.dataUnavailable()).isFalse();
        assertThat(outcome.events()).extracting(SignalEvent::getType).contains("DECOUPLING");
        SignalEvent decoupling = outcome.events().stream()
                .filter(e -> e.getType().equals("DECOUPLING")).findFirst().orElseThrow();
        assertThat(decoupling.getSeverity()).isBetween(0, 100);

        SignalEvidence evidence = signalDetectionService.evidenceFor(decoupling.getId()).orElseThrow();
        assertThat(evidence.getDeviation().doubleValue()).isNegative();
    }

    @Test
    void rallyDayProducesSilenceForTheScriptedSilentStock() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.SILENT_SYMBOL;

        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());
        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        assertThat(outcome.events()).extracting(SignalEvent::getType).contains("SILENCE");
    }

    @Test
    void detectingTwiceForTheSameLatestSnapshotDoesNotDuplicateEvents() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;
        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());

        DetectionOutcome first = signalDetectionService.detectForSymbol(symbol);
        DetectionOutcome second = signalDetectionService.detectForSymbol(symbol);

        assertThat(first.events()).hasSameSizeAs(second.events());
        assertThat(first.events()).extracting(SignalEvent::getId).containsExactlyInAnyOrderElementsOf(
                second.events().stream().map(SignalEvent::getId).toList());
    }

    @Test
    void normalPeerOnRallyDayDoesNotDecoupleOrGoSilent() {
        demoProvider.resetToStart();
        String symbol = "HIML"; // a normal participant in the rally, per Phase 3 scenario

        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());
        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        assertThat(outcome.events()).extracting(SignalEvent::getType).doesNotContain("DECOUPLING", "SILENCE");
    }

    @Test
    void detectingForSymbolWithNoPriorIngestionSelfBackfillsAndStillWorks() {
        // Regression test for a real production bug: detectForSymbol used to
        // only READ market_snapshots, never ingest them - so any caller that
        // never separately hit the market endpoints (the demo scenario, the
        // ledger, reconciliation) always found an empty snapshot table and
        // silently produced zero signals. detectForSymbol must now be
        // self-sufficient: even with ZERO prior manual ingestion, it should
        // backfill enough history itself and produce a real result.
        demoProvider.resetToStart();
        String symbol = "NIRM"; // deliberately NOT pre-ingested by this test at all

        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        assertThat(outcome.dataUnavailable()).isFalse();
        // Whether or not NIRM happens to have a triggered signal on this
        // specific day is a property of the real deterministic data, not
        // something this test should assert on - what matters is that
        // detection actually RAN (allCandidates present) rather than
        // silently short-circuiting on "no data".
    }

    @Test
    void freshRallyDayIngestionYieldsFullConfidence() {
        demoProvider.resetToStart();
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;
        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());

        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        assertThat(outcome.confidence()).isEqualTo(1.0);
        assertThat(outcome.events()).allMatch(e -> e.getConfidence().doubleValue() == 1.0);
    }
}
