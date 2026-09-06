package com.undertow.attention.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.repository.SignalLedgerEntryRepository;
import com.undertow.attention.service.LedgerHysteresis.EntryState;
import com.undertow.market.repository.MarketSnapshotRepository;
import com.undertow.signals.entity.SignalEvent;
import com.undertow.signals.model.SignalType;
import com.undertow.signals.service.DetectionOutcome;
import com.undertow.signals.service.SignalCandidate;
import com.undertow.signals.service.SignalDetectionService;
import com.undertow.trust.model.TrustStatus;

/**
 * Maintains one per-user ledger entry per (symbol, signal type) "episode" -
 * from first detection through to resolution. This is the layer that turns
 * the shared, symbol-scoped signal engine (Phases 4/5) into per-user
 * attention state.
 *
 * Three guarantees this class is responsible for, all directly tested:
 *   1. UNAVAILABLE data freezes every open entry at UNVERIFIED, touching
 *      nothing else - never a silent resolve or escalation (spec section 5,
 *      extended here to the ledger).
 *   2. Returning from UNVERIFIED always lands on ACTIVE first, never
 *      straight to RESOLVED or WORSENED, and hysteresis streaks restart
 *      fresh from that point.
 *   3. Syncing twice against the same underlying market data (same
 *      snapshot date) is a true no-op - persistence/resolve streaks only
 *      advance when the data has actually moved forward a day, never merely
 *      because the API was called again.
 *
 * The persistence THRESHOLD used per (user, type) is personalized (Phase
 * 11, PersonalizationService) - but the SEVERITY each candidate carries is
 * always the shared engine's output, untouched. Personalization changes
 * where this user's hysteresis draws its own line, never what the engine
 * itself computed.
 */
@Service
public class AttentionLedgerService {

    private final SignalDetectionService signalDetectionService;
    private final SignalLedgerEntryRepository ledgerRepository;
    private final MarketSnapshotRepository snapshotRepository;
    private final PersonalizationService personalizationService;

    private final int basePersistThreshold;
    private final int resolveThreshold;
    private final int worsenDeltaPoints;

    public AttentionLedgerService(
            SignalDetectionService signalDetectionService,
            SignalLedgerEntryRepository ledgerRepository,
            MarketSnapshotRepository snapshotRepository,
            PersonalizationService personalizationService,
            @Value("${undertow.attention.persistence-threshold:70}") int basePersistThreshold,
            @Value("${undertow.attention.resolve-threshold:50}") int resolveThreshold,
            @Value("${undertow.attention.worsen-delta-points:15}") int worsenDeltaPoints
    ) {
        this.signalDetectionService = signalDetectionService;
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.personalizationService = personalizationService;
        this.basePersistThreshold = basePersistThreshold;
        this.resolveThreshold = resolveThreshold;
        this.worsenDeltaPoints = worsenDeltaPoints;
    }

    /**
     * Brings this user's ledger for one symbol up to date against the
     * symbol's current shared signal state. Safe to call repeatedly - see
     * class-level guarantees.
     */
    @Transactional
    public List<SignalLedgerEntry> sync(UUID userId, String symbol) {
        DetectionOutcome outcome = signalDetectionService.detectForSymbol(symbol);

        for (SignalType type : SignalType.values()) {
            syncOneType(userId, symbol, type, outcome);
        }

        return ledgerRepository.findByUserIdAndSymbolOrderByLastDetectedAtDesc(userId, symbol);
    }

    private void syncOneType(UUID userId, String symbol, SignalType type, DetectionOutcome outcome) {
        Optional<SignalLedgerEntry> openEntry = findOpenEntry(userId, symbol, type);

        if (outcome.dataUnavailable()) {
            openEntry.ifPresent(entry -> freezeAsUnverified(entry, outcome.trustStatus()));
            return; // nothing else to do without real data - never resolve/escalate on missing data
        }

        Optional<SignalCandidate> shadow = outcome.allCandidates().stream()
                .filter(c -> c.type() == type)
                .findFirst();
        if (shadow.isEmpty()) {
            return; // insufficient history at the engine level - nothing to track yet
        }

        Instant currentAsOf = latestSnapshotAsOf(symbol);
        int severity = shadow.get().severity();
        String trustStatusName = outcome.trustStatus().name();

        if (openEntry.isEmpty()) {
            if (shadow.get().triggered()) {
                createEntry(userId, symbol, type, severity, trustStatusName, currentAsOf, outcome.events());
            }
            return;
        }

        SignalLedgerEntry entry = openEntry.get();

        if (isSameAsLastVerifiedCheck(entry, currentAsOf)) {
            return; // idempotent no-op: this exact day's data was already processed
        }

        if (LedgerStatus.valueOf(entry.getStatus()) == LedgerStatus.UNVERIFIED) {
            resumeFromUnverified(userId, type, entry, severity, trustStatusName, currentAsOf);
        } else {
            applyUpdate(userId, type, entry, severity, trustStatusName, currentAsOf);
        }
    }

    private Optional<SignalLedgerEntry> findOpenEntry(UUID userId, String symbol, SignalType type) {
        return ledgerRepository.findFirstByUserIdAndSymbolAndSignalTypeAndStatusNotOrderByFirstDetectedAtDesc(
                userId, symbol, type.name(), LedgerStatus.RESOLVED.name());
    }

    private boolean isSameAsLastVerifiedCheck(SignalLedgerEntry entry, Instant currentAsOf) {
        return entry.getLastVerifiedAsOf() != null && entry.getLastVerifiedAsOf().equals(currentAsOf);
    }

    private void freezeAsUnverified(SignalLedgerEntry entry, TrustStatus trustStatus) {
        if (LedgerStatus.valueOf(entry.getStatus()) == LedgerStatus.UNVERIFIED) {
            return; // already frozen - idempotent, touch nothing
        }
        entry.setStatus(LedgerStatus.UNVERIFIED.name());
        entry.setVerificationStatus(trustStatus.name());
        // Deliberately does NOT touch currentSeverity, previousSeverity,
        // maxSeverity, persistenceCount, resolveStreak, lastDetectedAt, or
        // lastVerifiedAsOf - this is the freeze.
        ledgerRepository.save(entry);
    }

    private void createEntry(UUID userId, String symbol, SignalType type, int severity,
                              String trustStatusName, Instant currentAsOf, List<SignalEvent> currentEvents) {
        int persistThreshold = personalizationService.effectivePersistThreshold(userId, type, basePersistThreshold);
        EntryState state = LedgerHysteresis.create(severity, persistThreshold, resolveThreshold);

        SignalLedgerEntry entry = new SignalLedgerEntry(userId, symbol, type.name());
        entry.setStatus(state.status().name());
        entry.setCurrentSeverity(state.currentSeverity());
        entry.setMaxSeverity(state.maxSeverity());
        entry.setPersistenceCount(state.persistenceCount());
        entry.setResolveStreak(state.resolveStreak());
        entry.setWorsenedFlag(state.worsenedFlag());
        entry.setVerificationStatus(trustStatusName);
        entry.setLastVerifiedAsOf(currentAsOf);

        currentEvents.stream()
                .filter(e -> e.getType().equals(type.name()))
                .findFirst()
                .ifPresent(e -> entry.setLatestSignalEventId(e.getId()));

        ledgerRepository.save(entry);
    }

    private void resumeFromUnverified(UUID userId, SignalType type, SignalLedgerEntry entry, int severity,
                                       String trustStatusName, Instant currentAsOf) {
        int persistThreshold = personalizationService.effectivePersistThreshold(userId, type, basePersistThreshold);
        EntryState state = LedgerHysteresis.resumeFromUnverified(entry.getMaxSeverity(), severity, persistThreshold, resolveThreshold);
        applyState(entry, entry.getCurrentSeverity(), state, trustStatusName, currentAsOf);
    }

    private void applyUpdate(UUID userId, SignalType type, SignalLedgerEntry entry, int severity,
                              String trustStatusName, Instant currentAsOf) {
        int persistThreshold = personalizationService.effectivePersistThreshold(userId, type, basePersistThreshold);
        EntryState previous = new EntryState(
                LedgerStatus.valueOf(entry.getStatus()), entry.getCurrentSeverity(), entry.getMaxSeverity(),
                entry.getPersistenceCount(), entry.getResolveStreak(), entry.isWorsenedFlag());

        EntryState next = LedgerHysteresis.update(previous, severity, persistThreshold, resolveThreshold, worsenDeltaPoints);
        applyState(entry, entry.getCurrentSeverity(), next, trustStatusName, currentAsOf);
    }

    private void applyState(SignalLedgerEntry entry, int previousSeverityBeforeThisUpdate, EntryState next,
                             String trustStatusName, Instant currentAsOf) {
        entry.setPreviousSeverity(previousSeverityBeforeThisUpdate);
        entry.setCurrentSeverity(next.currentSeverity());
        entry.setMaxSeverity(next.maxSeverity());
        entry.setPersistenceCount(next.persistenceCount());
        entry.setResolveStreak(next.resolveStreak());
        entry.setWorsenedFlag(next.worsenedFlag());
        entry.setStatus(next.status().name());
        entry.setVerificationStatus(trustStatusName);
        entry.setLastDetectedAt(Instant.now());
        entry.setLastVerifiedAsOf(currentAsOf);
        if (next.status() == LedgerStatus.RESOLVED) {
            entry.setResolvedAt(Instant.now());
        }
        ledgerRepository.save(entry);
    }

    private Instant latestSnapshotAsOf(String symbol) {
        return snapshotRepository.findTopBySymbolOrderByAsOfDesc(symbol)
                .map(s -> s.getAsOf())
                .orElse(null);
    }

    public List<SignalLedgerEntry> listForUser(UUID userId) {
        return ledgerRepository.findByUserIdOrderByLastDetectedAtDesc(userId);
    }

    public Optional<SignalLedgerEntry> acknowledge(UUID userId, UUID entryId) {
        Optional<SignalLedgerEntry> result = ledgerRepository.findByIdAndUserId(entryId, userId).map(entry -> {
            entry.setAcknowledged(true);
            entry.setAcknowledgedAt(Instant.now());
            return ledgerRepository.save(entry);
        });
        result.ifPresent(e -> personalizationService.recalibrate(userId));
        return result;
    }

    public Optional<SignalLedgerEntry> dismiss(UUID userId, UUID entryId) {
        Optional<SignalLedgerEntry> result = ledgerRepository.findByIdAndUserId(entryId, userId).map(entry -> {
            entry.setDismissed(true);
            return ledgerRepository.save(entry);
        });
        result.ifPresent(e -> personalizationService.recalibrate(userId));
        return result;
    }

    public Optional<SignalLedgerEntry> keepWatching(UUID userId, UUID entryId) {
        return ledgerRepository.findByIdAndUserId(entryId, userId).map(entry -> {
            entry.setDismissed(false);
            return ledgerRepository.save(entry);
        });
    }
}
