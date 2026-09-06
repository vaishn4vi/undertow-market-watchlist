package com.undertow.attention.service;

import com.undertow.attention.model.LedgerStatus;

/**
 * Pure state-transition logic for one signal ledger entry. Deliberately
 * isolated from persistence so it can be exhaustively unit-tested without a
 * database - the same pattern used for DeterministicMarketSimulator and
 * SignalEngine.
 *
 * Design:
 *   - persistenceCount counts CONSECUTIVE verified readings at/above the
 *     persistence threshold; it resets to 0 the moment a reading falls
 *     below it. Reaching 2 promotes ACTIVE -> PERSISTED.
 *   - resolveStreak counts CONSECUTIVE verified readings at/below the
 *     resolve threshold; it resets to 0 the moment a reading rises above
 *     it. Reaching 2 resolves the entry from any non-terminal status.
 *   - A reading in the dead zone between the two thresholds (resolve
 *     threshold, persistence threshold) resets BOTH streaks - it's neither
 *     confirming persistence nor confirming resolution, so it shouldn't
 *     silently count toward either one (this is what prevents flapping).
 *   - worsenedFlag is a one-way historical marker: once true, it stays true
 *     for the entry's lifetime, even after status moves on to PERSISTED or
 *     RESOLVED. The transient WORSENED *status* is shown for the one check
 *     where the spike was detected; subsequent checks continue the normal
 *     progression based on the new severity level.
 *   - RESOLVED is terminal - callers never invoke update() on a resolved
 *     entry (the ledger only tracks "open" entries).
 */
public final class LedgerHysteresis {

    private LedgerHysteresis() {
    }

    public record EntryState(
            LedgerStatus status,
            int currentSeverity,
            int maxSeverity,
            int persistenceCount,
            int resolveStreak,
            boolean worsenedFlag
    ) {
    }

    /** First time a signal type is observed for a user - always starts NEW. */
    public static EntryState create(int severity, int persistThreshold, int resolveThreshold) {
        return new EntryState(
                LedgerStatus.NEW,
                severity,
                severity,
                severity >= persistThreshold ? 1 : 0,
                severity <= resolveThreshold ? 1 : 0,
                false
        );
    }

    /**
     * An entry returning from UNVERIFIED once data becomes available again.
     * Lands on ACTIVE regardless of the math - never auto-resolved or
     * auto-escalated on the very reading that ends an unverified gap (spec
     * section 5's resilience requirement extended to the ledger). Streaks
     * restart fresh from this point; carrying over pre-gap streaks would
     * effectively let stale, unverified time count toward a real transition.
     */
    public static EntryState resumeFromUnverified(int previousMaxSeverity, int severity, int persistThreshold, int resolveThreshold) {
        return new EntryState(
                LedgerStatus.ACTIVE,
                severity,
                Math.max(previousMaxSeverity, severity),
                severity >= persistThreshold ? 1 : 0,
                severity <= resolveThreshold ? 1 : 0,
                false
        );
    }

    /** Normal ongoing update for an entry that was not UNVERIFIED. */
    public static EntryState update(EntryState previous, int newSeverity, int persistThreshold, int resolveThreshold, int worsenDeltaPoints) {
        boolean worsened = (newSeverity - previous.currentSeverity() >= worsenDeltaPoints)
                || (previous.currentSeverity() < persistThreshold && newSeverity >= persistThreshold);

        int newPersistenceCount = newSeverity >= persistThreshold ? previous.persistenceCount() + 1 : 0;
        int newResolveStreak = newSeverity <= resolveThreshold ? previous.resolveStreak() + 1 : 0;
        int newMaxSeverity = Math.max(previous.maxSeverity(), newSeverity);
        boolean newWorsenedFlag = previous.worsenedFlag() || worsened;

        LedgerStatus newStatus;
        if (newResolveStreak >= 2) {
            newStatus = LedgerStatus.RESOLVED;
        } else if (worsened) {
            newStatus = LedgerStatus.WORSENED;
        } else if (newPersistenceCount >= 2) {
            newStatus = LedgerStatus.PERSISTED;
        } else {
            newStatus = LedgerStatus.ACTIVE;
        }

        return new EntryState(newStatus, newSeverity, newMaxSeverity, newPersistenceCount, newResolveStreak, newWorsenedFlag);
    }
}
