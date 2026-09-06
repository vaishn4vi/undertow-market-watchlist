package com.undertow.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.reconciliation.service.ReconciliationService;
import com.undertow.reconciliation.service.ReconciliationService.ReconciliationResult;
import com.undertow.watchlist.dto.AddWatchlistItemRequest;
import com.undertow.watchlist.dto.CreateWatchlistRequest;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.service.WatchlistService;

@SpringBootTest
@ActiveProfiles("test")
class ReconciliationServiceTest {

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Test
    void repeatedCheckinWithSameRequestIdIsIdempotent() {
        String user = "reconcile-user-1";
        String requestId = UUID.randomUUID().toString();

        ReconciliationResult first = reconciliationService.checkIn(user, requestId);
        ReconciliationResult second = reconciliationService.checkIn(user, requestId);

        assertThat(second.checkinId()).isEqualTo(first.checkinId());
        assertThat(second.events()).hasSameSizeAs(first.events());
    }

    @Test
    void firstCheckinWithNewSignalReportsNewlyDetected() {
        // Uses the real scripted demo decoupler (BHRT) rather than manually
        // injected synthetic dates - avoids any collision between synthetic
        // test dates and the auto-backfill that SignalDetectionService now
        // performs for real, provider-known symbols (see the fix in this
        // session: detectForSymbol used to only ever READ market_snapshots,
        // never ingest them, which is why the demo scenario produced zero
        // signals end-to-end before this fix).
        demoProvider.resetToStart();
        String user = "reconcile-user-2";
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL; // BHRT - reliably decouples on the rally day

        // SignalDetectionService.detectForSymbol() treats the latest-ever-
        // ingested snapshot for a symbol as "today" with no upper bound -
        // correct for a real system, where data only ever arrives in
        // chronological order. But this suite runs every @SpringBootTest
        // class against one shared H2 database with no per-test rollback,
        // and other tests (e.g. MarketDataServiceTest, which runs earlier
        // alphabetically) legitimately fast-forward BHRT's ingested history
        // all the way to the away-window's return day, where BHRT has
        // mean-reverted back to a non-triggering state. Without clearing
        // that leftover history first, this test's "today" would silently
        // resolve to someone else's later, already-resolved day instead of
        // the rally day this test actually means to check in on.
        snapshotRepository.deleteAll(snapshotRepository.findBySymbolOrderByAsOfAsc(symbol));

        watchlistService.createWatchlist(user, new CreateWatchlistRequest("Test"));
        Watchlist watchlist = watchlistService.listWatchlists(user).get(0);
        watchlistService.addItem(user, watchlist.getId(), new AddWatchlistItemRequest(symbol));

        ReconciliationResult result = reconciliationService.checkIn(user, UUID.randomUUID().toString());

        assertThat(result.checkinId()).isNotNull();
        assertThat(result.previousCheckinAt()).isNull(); // first-ever checkin for this user
        assertThat(result.newCount()).isGreaterThan(0);
        assertThat(result.events()).anyMatch(e -> e.outcome().equals("NEWLY_DETECTED") && e.symbol().equals(symbol));
    }

    @Test
    void secondCheckinHasAPreviousCheckinTimestamp() {
        String user = "reconcile-user-3";
        reconciliationService.checkIn(user, UUID.randomUUID().toString());
        ReconciliationResult second = reconciliationService.checkIn(user, UUID.randomUUID().toString());

        assertThat(second.previousCheckinAt()).isNotNull();
    }
}
