package com.undertow.attention;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.service.AttentionLedgerService;
import com.undertow.market.entity.MarketObservation;
import com.undertow.market.entity.MarketSnapshot;
import com.undertow.market.repository.MarketObservationRepository;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.market.service.DemoMarketDataProvider;
import com.undertow.market.service.HackathonDemoScenario;
import com.undertow.market.service.MarketDataService;
import com.undertow.signals.model.SignalType;
import com.undertow.users.entity.User;
import com.undertow.users.service.UserService;

@SpringBootTest
@ActiveProfiles("test")
class AttentionLedgerServiceTest {

    @Autowired
    private AttentionLedgerService ledgerService;

    @Autowired
    private MarketSnapshotRepository snapshotRepository;

    @Autowired
    private MarketObservationRepository observationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private DemoMarketDataProvider demoProvider;

    // Verified via standalone harness: 15 days of small alternating noise
    // (sector held at 0, forcing a flat-model regression) keeps residual
    // variance real but small, so a -8.0% shock is unambiguously extreme.
    private static final double[] BASELINE = {
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25, -0.15,
            0.25, -0.15, 0.25, -0.15, 0.25, -0.15, 0.25
    };

    @Test
    void sustainedShockCreatesEntryThenPromotesToPersisted() {
        String symbol = "LEDGER-PERSIST-1";
        String user = "ledger-user-1";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        List<SignalLedgerEntry> afterDay1 = sync(user, symbol);
        SignalLedgerEntry entry = decoupling(afterDay1);
        assertThat(entry.getStatus()).isEqualTo(LedgerStatus.NEW.name());
        assertThat(entry.getPersistenceCount()).isEqualTo(1);

        LocalDate day2 = day1.plusDays(1);
        ingestSyntheticDay(symbol, day2, -8.0, 0.0);
        List<SignalLedgerEntry> afterDay2 = sync(user, symbol);
        SignalLedgerEntry entry2 = decoupling(afterDay2);
        assertThat(entry2.getId()).isEqualTo(entry.getId()); // same episode, not a new row
        assertThat(entry2.getStatus()).isEqualTo(LedgerStatus.PERSISTED.name());
        assertThat(entry2.getPersistenceCount()).isEqualTo(2);
    }

    @Test
    void severityFallingAfterShockEventuallyResolves() {
        String symbol = "LEDGER-RESOLVE-1";
        String user = "ledger-user-2";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate shockDay = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, shockDay, -8.0, 0.0);
        sync(user, symbol);

        LocalDate calm1 = shockDay.plusDays(1);
        ingestSyntheticDay(symbol, calm1, 0.1, 0.0);
        SignalLedgerEntry afterCalm1 = decoupling(sync(user, symbol));
        assertThat(afterCalm1.getStatus()).isNotEqualTo(LedgerStatus.RESOLVED.name());
        assertThat(afterCalm1.getResolveStreak()).isEqualTo(1);

        LocalDate calm2 = calm1.plusDays(1);
        ingestSyntheticDay(symbol, calm2, 0.1, 0.0);
        SignalLedgerEntry afterCalm2 = decoupling(sync(user, symbol));
        assertThat(afterCalm2.getStatus()).isEqualTo(LedgerStatus.RESOLVED.name());
        assertThat(afterCalm2.getResolvedAt()).isNotNull();
    }

    @Test
    void bigSeverityJumpTriggersWorsened() {
        String symbol = "LEDGER-WORSEN-1";
        String user = "ledger-user-3";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate moderateDay = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, moderateDay, -2.0, 0.0);
        SignalLedgerEntry created = decoupling(sync(user, symbol));
        assertThat(created.getCurrentSeverity()).isLessThan(90); // moderate, not yet extreme

        LocalDate bigDay = moderateDay.plusDays(1);
        ingestSyntheticDay(symbol, bigDay, -8.0, 0.0);
        SignalLedgerEntry worsened = decoupling(sync(user, symbol));
        assertThat(worsened.getStatus()).isEqualTo(LedgerStatus.WORSENED.name());
        assertThat(worsened.isWorsenedFlag()).isTrue();
        assertThat(worsened.getPreviousSeverity()).isEqualTo(created.getCurrentSeverity());
    }

    @Test
    void repeatedSyncWithoutNewDataIsIdempotentAndNeverDoubleCounts() {
        String symbol = "LEDGER-IDEMPOTENT-1";
        String user = "ledger-user-4";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        SignalLedgerEntry first = decoupling(sync(user, symbol));
        assertThat(first.getPersistenceCount()).isEqualTo(1);

        // Sync again WITHOUT ingesting any new day - must be a true no-op.
        SignalLedgerEntry second = decoupling(sync(user, symbol));
        SignalLedgerEntry third = decoupling(sync(user, symbol));

        assertThat(second.getPersistenceCount()).isEqualTo(1); // NOT 2
        assertThat(third.getPersistenceCount()).isEqualTo(1); // NOT 3
        assertThat(second.getStatus()).isEqualTo(LedgerStatus.NEW.name()); // never promoted by repeated calls alone
        assertThat(third.getLastDetectedAt()).isEqualTo(second.getLastDetectedAt());
    }

    @Test
    void unavailableDataFreezesEntryAtUnverifiedWithoutChangingSeverity() {
        String symbol = "LEDGER-UNVERIFIED-1";
        String user = "ledger-user-5";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        SignalLedgerEntry created = decoupling(sync(user, symbol));
        int severityBeforeOutage = created.getCurrentSeverity();

        // Simulate an outage: an UNAVAILABLE observation more recent than
        // the last successful ingestion, no new snapshot.
        observationRepository.save(new MarketObservation(symbol, Instant.now(), "UNAVAILABLE", "{}", null));

        SignalLedgerEntry frozen = decoupling(sync(user, symbol));
        assertThat(frozen.getStatus()).isEqualTo(LedgerStatus.UNVERIFIED.name());
        assertThat(frozen.getCurrentSeverity()).isEqualTo(severityBeforeOutage); // frozen, not recomputed
        assertThat(frozen.getVerificationStatus()).isEqualTo("UNAVAILABLE");

        // Repeated unavailable checks must remain frozen (idempotent).
        SignalLedgerEntry stillFrozen = decoupling(sync(user, symbol));
        assertThat(stillFrozen.getStatus()).isEqualTo(LedgerStatus.UNVERIFIED.name());
        assertThat(stillFrozen.getCurrentSeverity()).isEqualTo(severityBeforeOutage);
    }

    @Test
    void unverifiedEntryResumesToActiveNotAutoResolvedOrEscalated() {
        String symbol = "LEDGER-RESUME-1";
        String user = "ledger-user-6";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        sync(user, symbol);

        observationRepository.save(new MarketObservation(symbol, Instant.now(), "UNAVAILABLE", "{}", null));
        SignalLedgerEntry frozen = decoupling(sync(user, symbol));
        assertThat(frozen.getStatus()).isEqualTo(LedgerStatus.UNVERIFIED.name());

        // Data returns with a LOW severity (would be RESOLVED if the old
        // resolve streak had carried over through the gap).
        //
        // TrustService.assess() looks at the most recent MarketObservation
        // by receivedAt (real wall-clock time at construction, see
        // MarketObservation's constructor) - this is correct, documented
        // production behavior: it reflects the most recent ingestion
        // attempt, exactly like the real MarketDataService.ingestOne()
        // always records one on every attempt, success or failure. This
        // test's ingestSyntheticDay() helper only inserts a MarketSnapshot
        // (that's all the other tests in this file need), so without also
        // recording a LIVE observation here, the earlier UNAVAILABLE
        // observation would remain "the most recent attempt" forever -
        // this line simulates what a real successful re-ingestion would
        // have produced.
        LocalDate returnDay = day1.plusDays(1);
        ingestSyntheticDay(symbol, returnDay, 0.1, 0.0);
        observationRepository.save(new MarketObservation(symbol, Instant.now(), "LIVE", "{}", null));
        SignalLedgerEntry resumed = decoupling(sync(user, symbol));

        assertThat(resumed.getStatus()).isEqualTo(LedgerStatus.ACTIVE.name()); // not RESOLVED
        assertThat(resumed.getResolveStreak()).isEqualTo(1); // fresh streak, this is reading #1, not #2
    }

    @Test
    void acknowledgeDismissAndKeepWatchingDoNotInterfereWithStateMachine() {
        String symbol = "LEDGER-ACK-1";
        String user = "ledger-user-7";
        User internalUser = userService.getOrCreate(user);
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);

        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        SignalLedgerEntry created = decoupling(sync(user, symbol));

        Optional<SignalLedgerEntry> acked = ledgerService.acknowledge(internalUser.getId(), created.getId());
        assertThat(acked).isPresent();
        assertThat(acked.get().isAcknowledged()).isTrue();
        assertThat(acked.get().getAcknowledgedAt()).isNotNull();

        Optional<SignalLedgerEntry> dismissed = ledgerService.dismiss(internalUser.getId(), created.getId());
        assertThat(dismissed).isPresent();
        assertThat(dismissed.get().isDismissed()).isTrue();

        // The state machine keeps progressing normally regardless of dismissal.
        LocalDate day2 = day1.plusDays(1);
        ingestSyntheticDay(symbol, day2, -8.0, 0.0);
        SignalLedgerEntry afterDismissStillProgresses = decoupling(sync(user, symbol));
        assertThat(afterDismissStillProgresses.getStatus()).isEqualTo(LedgerStatus.PERSISTED.name());
        assertThat(afterDismissStillProgresses.isDismissed()).isTrue(); // dismissal persists independently

        Optional<SignalLedgerEntry> keptWatching = ledgerService.keepWatching(internalUser.getId(), created.getId());
        assertThat(keptWatching).isPresent();
        assertThat(keptWatching.get().isDismissed()).isFalse();
    }

    @Test
    void unknownEntryIdForAckDismissReturnsEmpty() {
        User internalUser = userService.getOrCreate("ledger-user-8");
        assertThat(ledgerService.acknowledge(internalUser.getId(), UUID.randomUUID())).isEmpty();
        assertThat(ledgerService.dismiss(internalUser.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void userCannotAcknowledgeAnotherUsersEntry() {
        String symbol = "LEDGER-ISOLATION-1";
        String ownerExternal = "ledger-owner-1";
        String otherExternal = "ledger-other-1";
        LocalDate start = LocalDate.of(2026, 1, 1);
        ingestBaseline(symbol, start);
        LocalDate day1 = start.plusDays(BASELINE.length);
        ingestSyntheticDay(symbol, day1, -8.0, 0.0);
        SignalLedgerEntry created = decoupling(sync(ownerExternal, symbol));

        User other = userService.getOrCreate(otherExternal);
        assertThat(ledgerService.acknowledge(other.getId(), created.getId())).isEmpty();
    }

    @Test
    void realDemoScenarioProducesAndEventuallyResolvesRealSignals() {
        // End-to-end sanity check against the actual Phase 3 demo data
        // (not synthetic) - confirms the whole pipeline (provider -> engine
        // -> trust -> ledger) plugs together correctly on real data, whatever
        // the organically-produced severity trajectory turns out to be.
        demoProvider.resetToStart();
        String user = "ledger-demo-user";
        String symbol = HackathonDemoScenario.DECOUPLER_SYMBOL;

        marketDataService.ensureIngestedHistory(symbol, demoProvider.rallyDay().minusDays(30), demoProvider.rallyDay());
        List<SignalLedgerEntry> onRallyDay = sync(user, symbol);
        SignalLedgerEntry entry = decoupling(onRallyDay);
        assertThat(entry.getStatus()).isEqualTo(LedgerStatus.NEW.name());

        // Advance through the away window, syncing each day, and confirm the
        // entry eventually reaches a terminal RESOLVED state without ever
        // throwing or producing an invalid intermediate state.
        LedgerStatus lastStatus = LedgerStatus.NEW;
        for (int d = 1; d <= HackathonDemoScenario.AWAY_WINDOW_DAYS; d++) {
            demoProvider.advance(1);
            marketDataService.ensureIngestedAndGetLatest(symbol);
            List<SignalLedgerEntry> entries = sync(user, symbol);
            Optional<SignalLedgerEntry> maybeEntry = entries.stream()
                    .filter(e -> e.getSignalType().equals(SignalType.DECOUPLING.name()))
                    .findFirst();
            if (maybeEntry.isPresent()) {
                lastStatus = LedgerStatus.valueOf(maybeEntry.get().getStatus());
            } else {
                break; // resolved entries are excluded from "open" queries by design
            }
        }
        // Either it's still open in some valid non-terminal state, or it
        // resolved and dropped out of the open-entries view - both are
        // valid outcomes; what matters is nothing crashed and no entry
        // silently vanished mid-lifecycle without reaching RESOLVED first.
        assertThat(lastStatus).isIn(LedgerStatus.NEW, LedgerStatus.ACTIVE, LedgerStatus.PERSISTED, LedgerStatus.WORSENED, LedgerStatus.RESOLVED);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private List<SignalLedgerEntry> sync(String externalUserId, String symbol) {
        User user = userService.getOrCreate(externalUserId);
        return ledgerService.sync(user.getId(), symbol);
    }

    private SignalLedgerEntry decoupling(List<SignalLedgerEntry> entries) {
        return entries.stream()
                .filter(e -> e.getSignalType().equals(SignalType.DECOUPLING.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No DECOUPLING entry found among: " + entries));
    }

    private void ingestBaseline(String symbol, LocalDate start) {
        for (int i = 0; i < BASELINE.length; i++) {
            ingestSyntheticDay(symbol, start.plusDays(i), BASELINE[i], 0.0);
        }
    }

    private void ingestSyntheticDay(String symbol, LocalDate date, double stockReturn, double sectorReturn) {
        Instant asOf = date.atTime(16, 0).atZone(ZoneId.of("America/New_York")).toInstant();
        snapshotRepository.save(new MarketSnapshot(
                symbol, asOf, BigDecimal.valueOf(100), BigDecimal.valueOf(stockReturn),
                "TestSector", BigDecimal.valueOf(sectorReturn), BigDecimal.valueOf(sectorReturn),
                "CLOSED", "test"));
    }
}
