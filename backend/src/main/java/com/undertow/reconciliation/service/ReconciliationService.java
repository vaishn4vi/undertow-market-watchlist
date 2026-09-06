package com.undertow.reconciliation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.service.AttentionDebtService;
import com.undertow.attention.service.AttentionDebtService.DebtWithEntries;
import com.undertow.attention.service.AttentionLedgerService;
import com.undertow.reconciliation.entity.Checkin;
import com.undertow.reconciliation.entity.SignalReconciliation;
import com.undertow.reconciliation.repository.CheckinRepository;
import com.undertow.reconciliation.repository.SignalReconciliationRepository;
import com.undertow.users.entity.User;
import com.undertow.users.service.UserService;
import com.undertow.watchlist.entity.Watchlist;
import com.undertow.watchlist.entity.WatchlistItem;
import com.undertow.watchlist.repository.WatchlistItemRepository;
import com.undertow.watchlist.repository.WatchlistRepository;

/**
 * Reconciles a user's watchlist against the shared signal state on every
 * check-in: syncs the per-user ledger for every watched symbol, then diffs
 * ledger entries against their state as of the last check-in to produce a
 * structured "since you last checked" summary. Idempotent per request_id -
 * replaying the same check-in never re-runs sync logic or creates duplicate
 * reconciliation rows, it just replays the stored result.
 */
@Service
public class ReconciliationService {

    private final CheckinRepository checkinRepository;
    private final SignalReconciliationRepository reconciliationRepository;
    private final UserService userService;
    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final AttentionLedgerService ledgerService;
    private final AttentionDebtService debtService;

    public ReconciliationService(
            CheckinRepository checkinRepository,
            SignalReconciliationRepository reconciliationRepository,
            UserService userService,
            WatchlistRepository watchlistRepository,
            WatchlistItemRepository watchlistItemRepository,
            AttentionLedgerService ledgerService,
            AttentionDebtService debtService
    ) {
        this.checkinRepository = checkinRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.userService = userService;
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.ledgerService = ledgerService;
        this.debtService = debtService;
    }

    public record ReconciliationEvent(
            String symbol, String signalType, String outcome,
            Integer severityBefore, Integer severityAfter, String narrative) {
    }

    public record ReconciliationResult(
            UUID checkinId, BigDecimal daysAway, Instant previousCheckinAt,
            List<ReconciliationEvent> events, int resolvedCount, int activeCount,
            int worsenedCount, int unverifiableCount, int newCount,
            double debtBefore, double debtAfter, String trajectory) {
    }

    @Transactional
    public ReconciliationResult checkIn(String externalUserId, String requestId) {
        return checkinRepository.findByRequestId(requestId)
                .map(this::replay)
                .orElseGet(() -> performCheckIn(externalUserId, requestId));
    }

    private ReconciliationResult performCheckIn(String externalUserId, String requestId) {
        User user = userService.getOrCreate(externalUserId);
        UUID userId = user.getId();

        var previousCheckin = checkinRepository.findFirstByUserIdOrderByCheckinAtDesc(userId);
        Instant previousCheckinAt = previousCheckin.map(Checkin::getCheckinAt).orElse(null);

        double debtBefore = debtService.compute(userId).result().normalizedDebt();

        List<String> symbols = watchedSymbols(userId);

        // Snapshot each open entry's state BEFORE syncing, keyed by (symbol, type).
        Map<String, EntrySnapshot> before = new HashMap<>();
        for (String symbol : symbols) {
            for (SignalLedgerEntry e : ledgerService.listForUser(userId)) {
                if (e.getSymbol().equals(symbol)) {
                    before.put(key(e), new EntrySnapshot(e.getStatus(), e.getCurrentSeverity(), e.isWorsenedFlag()));
                }
            }
        }

        for (String symbol : symbols) {
            ledgerService.sync(userId, symbol);
        }

        List<SignalLedgerEntry> allEntriesAfter = ledgerService.listForUser(userId).stream()
                .filter(e -> symbols.contains(e.getSymbol()))
                .toList();

        List<ReconciliationEvent> events = new ArrayList<>();
        int resolved = 0, active = 0, worsened = 0, unverifiable = 0, fresh = 0;

        for (SignalLedgerEntry entry : allEntriesAfter) {
            EntrySnapshot prior = before.get(key(entry));
            String outcome;
            if (prior == null) {
                outcome = "NEWLY_DETECTED";
                fresh++;
            } else if (entry.getStatus().equals(LedgerStatus.RESOLVED.name()) && !prior.status.equals(LedgerStatus.RESOLVED.name())) {
                outcome = "RESOLVED";
                resolved++;
            } else if (entry.getStatus().equals(LedgerStatus.UNVERIFIED.name())) {
                outcome = "UNVERIFIABLE";
                unverifiable++;
            } else if (entry.isWorsenedFlag() && !prior.worsenedFlag) {
                outcome = "WORSENED";
                worsened++;
            } else {
                outcome = "STILL_ACTIVE";
                active++;
            }

            Integer severityBefore = prior != null ? prior.severity : null;
            Integer severityAfter = entry.getCurrentSeverity();
            events.add(new ReconciliationEvent(
                    entry.getSymbol(), entry.getSignalType(), outcome, severityBefore, severityAfter,
                    narrative(entry.getSymbol(), entry.getSignalType(), outcome, severityBefore, severityAfter)));
        }

        // Entries that were open before but are no longer in the after-list
        // at all (fully dropped from "open" because they resolved) still
        // need to be captured - listForUser excludes nothing, but a RESOLVED
        // entry stays visible (RESOLVED is terminal, not deleted), so this
        // is already handled by the loop above. No further action needed.

        BigDecimal daysAway = previousCheckinAt != null
                ? BigDecimal.valueOf(Duration.between(previousCheckinAt, Instant.now()).toMinutes() / 1440.0)
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Checkin checkin = checkinRepository.save(new Checkin(userId, requestId, previousCheckinAt, daysAway));

        DebtWithEntries afterDebt = debtService.computeAndSnapshot(userId);

        for (ReconciliationEvent event : events) {
            SignalLedgerEntry matching = allEntriesAfter.stream()
                    .filter(e -> e.getSymbol().equals(event.symbol()) && e.getSignalType().equals(event.signalType()))
                    .findFirst().orElseThrow();
            reconciliationRepository.save(new SignalReconciliation(
                    checkin.getId(), matching.getId(), event.outcome(),
                    event.severityBefore(), event.severityAfter(), event.narrative()));
        }

        return new ReconciliationResult(
                checkin.getId(), daysAway, previousCheckinAt, events,
                resolved, active, worsened, unverifiable, fresh,
                round(debtBefore), round(afterDebt.result().normalizedDebt()), afterDebt.result().trajectory().name());
    }

    private ReconciliationResult replay(Checkin checkin) {
        List<SignalReconciliation> rows = reconciliationRepository.findByCheckinId(checkin.getId());
        List<ReconciliationEvent> events = new ArrayList<>();
        int resolved = 0, active = 0, worsened = 0, unverifiable = 0, fresh = 0;

        for (SignalReconciliation row : rows) {
            var entry = ledgerService.listForUser(checkin.getUserId()).stream()
                    .filter(e -> e.getId().equals(row.getSignalLedgerEntryId()))
                    .findFirst();
            String symbol = entry.map(SignalLedgerEntry::getSymbol).orElse("UNKNOWN");
            String type = entry.map(SignalLedgerEntry::getSignalType).orElse("UNKNOWN");

            switch (row.getOutcome()) {
                case "RESOLVED" -> resolved++;
                case "STILL_ACTIVE" -> active++;
                case "WORSENED" -> worsened++;
                case "UNVERIFIABLE" -> unverifiable++;
                case "NEWLY_DETECTED" -> fresh++;
                default -> {
                }
            }
            events.add(new ReconciliationEvent(symbol, type, row.getOutcome(), row.getSeverityBefore(),
                    row.getSeverityAfter(), row.getNarrativeText()));
        }

        // Debt before/after aren't re-derivable perfectly on replay without
        // re-querying snapshot history around this checkin's timestamp; a
        // best-effort current read is used instead, since replay is about
        // idempotent event data, not re-deriving point-in-time debt exactly.
        double currentDebt = debtService.compute(checkin.getUserId()).result().normalizedDebt();

        return new ReconciliationResult(
                checkin.getId(), checkin.getDaysAway(), checkin.getPreviousCheckinAt(), events,
                resolved, active, worsened, unverifiable, fresh, round(currentDebt), round(currentDebt), "STABLE");
    }

    private List<String> watchedSymbols(UUID userId) {
        List<Watchlist> watchlists = watchlistRepository.findByUserIdOrderByPositionAsc(userId);
        List<String> symbols = new ArrayList<>();
        for (Watchlist w : watchlists) {
            for (WatchlistItem item : watchlistItemRepository.findByWatchlistIdOrderByPositionAsc(w.getId())) {
                if (!symbols.contains(item.getSymbol())) {
                    symbols.add(item.getSymbol());
                }
            }
        }
        return symbols;
    }

    private static String key(SignalLedgerEntry e) {
        return e.getSymbol() + "::" + e.getSignalType();
    }

    private static String narrative(String symbol, String type, String outcome, Integer before, Integer after) {
        return switch (outcome) {
            case "RESOLVED" -> symbol + "'s " + type.toLowerCase() + " signal has resolved.";
            case "WORSENED" -> symbol + "'s " + type.toLowerCase() + " signal has worsened (severity "
                    + before + " -> " + after + ").";
            case "NEWLY_DETECTED" -> symbol + " shows a new " + type.toLowerCase() + " signal (severity " + after + ").";
            case "UNVERIFIABLE" -> symbol + "'s " + type.toLowerCase()
                    + " signal could not be verified; previous severity carried forward.";
            default -> symbol + "'s " + type.toLowerCase() + " signal remains active (severity " + after + ").";
        };
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record EntrySnapshot(String status, int severity, boolean worsenedFlag) {
    }
}
