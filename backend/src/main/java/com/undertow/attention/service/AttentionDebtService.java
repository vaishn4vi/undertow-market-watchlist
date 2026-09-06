package com.undertow.attention.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.attention.entity.AttentionDebtSnapshot;
import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.model.DebtBand;
import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.repository.AttentionDebtSnapshotRepository;
import com.undertow.attention.repository.SignalLedgerEntryRepository;
import com.undertow.attention.service.AttentionDebtEngine.DebtResult;
import com.undertow.attention.service.AttentionDebtEngine.EntryWeight;

/**
 * Turns a user's open ledger entries into an Attention Debt score, and ranks
 * them into a capped priority queue when debt is high. Both concerns live
 * together because the cap itself depends on the computed band.
 */
@Service
public class AttentionDebtService {

    private static final double UNVERIFIED_CONFIDENCE_FACTOR = 0.5; // dampen debt contribution from frozen/unverified entries
    private static final double VERIFIED_CONFIDENCE_FACTOR = 1.0;

    private final SignalLedgerEntryRepository ledgerRepository;
    private final AttentionDebtSnapshotRepository snapshotRepository;
    private final double normalizationK;
    private final int lowCap;
    private final int moderateCap;
    private final int highCap;

    public AttentionDebtService(
            SignalLedgerEntryRepository ledgerRepository,
            AttentionDebtSnapshotRepository snapshotRepository,
            @Value("${undertow.attention.debt-normalization-k:6.0}") double normalizationK,
            @Value("${undertow.attention.priority-queue.low-cap:999}") int lowCap,
            @Value("${undertow.attention.priority-queue.moderate-cap:6}") int moderateCap,
            @Value("${undertow.attention.priority-queue.high-cap:3}") int highCap
    ) {
        this.ledgerRepository = ledgerRepository;
        this.snapshotRepository = snapshotRepository;
        this.normalizationK = normalizationK;
        this.lowCap = lowCap;
        this.moderateCap = moderateCap;
        this.highCap = highCap;
    }

    public record DebtWithEntries(DebtResult result, List<SignalLedgerEntry> openEntries) {
    }

    public record PriorityQueueResult(List<SignalLedgerEntry> shown, int deferredCount) {
    }

    /** Read-only computation - safe to call as often as needed (e.g. every dashboard load). */
    public DebtWithEntries compute(UUID userId) {
        List<SignalLedgerEntry> openEntries = openEntriesFor(userId);
        Double previous = snapshotRepository.findFirstByUserIdOrderByComputedAtDesc(userId)
                .map(s -> s.getNormalizedDebt().doubleValue())
                .orElse(null);

        List<EntryWeight> weights = openEntries.stream().map(this::toWeight).toList();
        DebtResult result = AttentionDebtEngine.compute(weights, previous, normalizationK);
        return new DebtWithEntries(result, openEntries);
    }

    /** Computes and persists a checkpoint - called at check-in time (Phase 8), not on every read. */
    @Transactional
    public DebtWithEntries computeAndSnapshot(UUID userId) {
        DebtWithEntries current = compute(userId);
        Optional<AttentionDebtSnapshot> lastSnapshot = snapshotRepository.findFirstByUserIdOrderByComputedAtDesc(userId);
        Instant since = lastSnapshot.map(AttentionDebtSnapshot::getComputedAt).orElse(Instant.EPOCH);

        double newComponent = current.openEntries().stream()
                .filter(e -> e.getStatus().equals(LedgerStatus.NEW.name()) && e.getFirstDetectedAt().isAfter(since))
                .mapToDouble(this::toWeight0)
                .sum();
        double worsenedComponent = current.openEntries().stream()
                .filter(e -> e.isWorsenedFlag() && e.getLastDetectedAt().isAfter(since))
                .mapToDouble(this::toWeight0)
                .sum();
        // Resolved entries are, by definition, no longer in openEntriesFor() -
        // look them up separately for the "debt removed" component.
        double resolvedComponent = ledgerRepository.findByUserIdOrderByLastDetectedAtDesc(userId).stream()
                .filter(e -> e.getStatus().equals(LedgerStatus.RESOLVED.name())
                        && e.getResolvedAt() != null && e.getResolvedAt().isAfter(since))
                .mapToDouble(e -> (e.getMaxSeverity() / 100.0) * VERIFIED_CONFIDENCE_FACTOR)
                .sum();

        AttentionDebtSnapshot snapshot = new AttentionDebtSnapshot(
                userId,
                bd(current.result().rawDebt()),
                bd(current.result().normalizedDebt()),
                current.result().band().name(),
                current.result().trajectory().name(),
                bd(newComponent),
                bd(worsenedComponent),
                bd(resolvedComponent)
        );
        snapshotRepository.save(snapshot);
        return current;
    }

    public List<AttentionDebtSnapshot> history(UUID userId) {
        return snapshotRepository.findByUserIdOrderByComputedAtAsc(userId);
    }

    /** Ranks open entries by severity and caps the list according to the current band. */
    public PriorityQueueResult prioritize(List<SignalLedgerEntry> openEntries, DebtBand band) {
        int cap = switch (band) {
            case LOW -> lowCap;
            case MODERATE -> moderateCap;
            case HIGH, OVERLOADED -> highCap;
        };

        List<SignalLedgerEntry> sorted = openEntries.stream()
                .filter(e -> !e.isDismissed())
                .sorted(Comparator.comparingInt(SignalLedgerEntry::getCurrentSeverity).reversed())
                .toList();

        List<SignalLedgerEntry> shown = sorted.stream().limit(cap).toList();
        int deferred = Math.max(0, sorted.size() - shown.size());
        return new PriorityQueueResult(shown, deferred);
    }

    private List<SignalLedgerEntry> openEntriesFor(UUID userId) {
        return ledgerRepository.findByUserIdOrderByLastDetectedAtDesc(userId).stream()
                .filter(e -> !e.getStatus().equals(LedgerStatus.RESOLVED.name()))
                .toList();
    }

    private EntryWeight toWeight(SignalLedgerEntry entry) {
        double confidence = entry.getStatus().equals(LedgerStatus.UNVERIFIED.name())
                ? UNVERIFIED_CONFIDENCE_FACTOR : VERIFIED_CONFIDENCE_FACTOR;
        return new EntryWeight(entry.getCurrentSeverity(), confidence, entry.getPersistenceCount());
    }

    private double toWeight0(SignalLedgerEntry entry) {
        return toWeight(entry).weight();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
