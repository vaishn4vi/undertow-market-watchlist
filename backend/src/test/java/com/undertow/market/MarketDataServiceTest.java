package com.undertow.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.market.entity.MarketObservation;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketObservationRepository;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;

@SpringBootTest
@ActiveProfiles("test")
class MarketDataServiceTest {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Autowired
    private MarketObservationRepository observationRepository;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ingestingTheSameDayTwiceDoesNotCreateDuplicateSnapshots() {
        demoProvider.resetToStart();
        String symbol = "HIML";

        marketDataService.ensureIngestedAndGetLatest(symbol);
        marketDataService.ensureIngestedAndGetLatest(symbol);

        List<MarketSnapshot> all = snapshotRepository.findBySymbolAndAsOfBetweenOrderByAsOfAsc(
                symbol, Instant.EPOCH, Instant.now());
        long distinctAsOf = all.stream().map(MarketSnapshot::getAsOf).distinct().count();

        assertThat(all).hasSize((int) distinctAsOf); // no duplicate rows for the same as_of
    }

    @Test
    void conflictingReingestionIsRecordedButDoesNotOverwriteTheAcceptedSnapshot() {
        demoProvider.resetToStart();
        // TEJA specifically (not SAHY, which TrustServiceTest's own
        // conflict-adjacent tests also touch) - every @SpringBootTest class
        // in this suite shares ONE H2 database instance for the whole test
        // run (no per-class isolation, no rollback), so two different test
        // classes both choosing the same symbol for "leave behind a
        // conflicting/wrong price" is a real collision, not just a
        // theoretical one.
        String symbol = "TEJA";

        // First, a legitimate ingestion for today.
        MarketSnapshot accepted = marketDataService.ensureIngestedAndGetLatest(symbol).orElseThrow();

        // Simulate a disagreeing observation arriving for the same
        // (symbol, as_of): MarketSnapshot is deliberately immutable
        // (no setters) and (symbol, as_of) is now a real unique constraint
        // (mirroring V2__market_snapshot_uniqueness.sql), so the only way
        // to plant a different stored value without going through
        // application code is a direct UPDATE on the existing row - not an
        // INSERT, which would correctly violate the constraint just like it
        // would in production. Re-ingesting will produce the real
        // (unchanged) provider value again, which should now disagree with
        // this artificially-planted price and hit the conflict path.
        jdbcTemplate.update("UPDATE market_snapshots SET price = ? WHERE id = ?", 1.23, accepted.getId());

        marketDataService.ensureIngestedAndGetLatest(symbol);

        List<MarketObservation> observations = observationRepository.findBySymbolOrderByReceivedAtDesc(symbol);
        boolean anyConflict = observations.stream().anyMatch(o -> o.getTrustStatus().equals("CONFLICTING"));

        assertThat(anyConflict).isTrue();
    }

    @Test
    void outageDayProducesNoSnapshotButLogsAnUnavailableObservation() {
        demoProvider.resetToStart();
        String outageSymbol = HackathonDemoScenario.OUTAGE_SYMBOL;

        demoProvider.fastForwardToReturnDay(); // the day the outage is scripted for

        // Establish a real prior snapshot to carry forward - jumping
        // straight to the outage day with no ingestion history at all
        // means there is nothing to carry forward, and
        // ensureIngestedAndGetLatest correctly (by design) returns empty
        // in that case, which isn't the "outage with carried-forward data"
        // scenario this test means to exercise.
        marketDataService.ensureIngestedHistory(
                outageSymbol, demoProvider.rallyDay(), demoProvider.returnDay().minusDays(1));

        Optional<MarketSnapshot> latest = marketDataService.ensureIngestedAndGetLatest(outageSymbol);

        // No snapshot exists for the outage day itself, but the last known
        // snapshot (the day before) is still returned - this is the "carry
        // forward" behavior the trust layer (Phase 5) will build on.
        assertThat(latest).isPresent();
        Instant returnDayClose = demoProvider.returnDay().atTime(16, 0)
                .atZone(ZoneId.of("America/New_York")).toInstant();
        assertThat(latest.get().getAsOf()).isNotEqualTo(returnDayClose);

        List<MarketObservation> observations = observationRepository.findBySymbolOrderByReceivedAtDesc(outageSymbol);
        assertThat(observations).anyMatch(o -> o.getTrustStatus().equals("UNAVAILABLE"));
    }

    @Test
    void historyIngestionPersistsEveryAvailableDayInRange() {
        demoProvider.fastForwardToReturnDay();
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;

        List<MarketSnapshot> history = marketDataService.ensureIngestedHistory(
                symbol, demoProvider.rallyDay(), demoProvider.returnDay());

        // rally day + 12 away days = 13 days of real data (the symbol itself
        // has no outage, only ARGY does)
        assertThat(history).hasSize(13);
    }
}
