package com.undertow.attention.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.undertow.attention.entity.SignalLedgerEntry;
import com.undertow.attention.repository.SignalLedgerEntryRepository;
import com.undertow.signals.model.SignalType;
import com.undertow.users.entity.UserPreferences;
import com.undertow.users.repository.UserPreferencesRepository;

/**
 * Simple, fully deterministic, explainable adaptation - no ML. Operates
 * entirely on this user's own per-type persistence threshold within their
 * own ledger (UserPreferences.*ThresholdDelta), never on the shared
 * SignalEngine's detection gates - preserving the O(symbols) shared
 * computation architecture (spec: signal computation must not be
 * recalculated independently per user).
 *
 * Rule: if a user has closed (acknowledged or dismissed) at least 3 signals
 * of a given type and dismisses more than 70% of them, that type's
 * effective persistence threshold rises by 5 points (max +20) - the ledger
 * requires a stronger, more sustained signal before calling it "persisted"
 * for this user. If they dismiss fewer than 30% (i.e. mostly act on them),
 * the threshold falls by 5 points (max -10) - the ledger becomes more
 * sensitive for this user and this type.
 */
@Service
public class PersonalizationService {

    private static final int MIN_SAMPLES = 3;
    private static final double HIGH_DISMISS_RATE = 0.7;
    private static final double LOW_DISMISS_RATE = 0.3;
    private static final int ADJUSTMENT_STEP = 5;
    private static final int MAX_DELTA = 20;
    private static final int MIN_DELTA = -10;
    private static final int BASE_PERSIST_THRESHOLD_FLOOR = 50;
    private static final int BASE_PERSIST_THRESHOLD_CEILING = 90;

    private final UserPreferencesRepository preferencesRepository;
    private final SignalLedgerEntryRepository ledgerRepository;

    public PersonalizationService(UserPreferencesRepository preferencesRepository,
                                   SignalLedgerEntryRepository ledgerRepository) {
        this.preferencesRepository = preferencesRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public UserPreferences getOrCreate(UUID userId) {
        return preferencesRepository.findByUserId(userId).orElseGet(() -> preferencesRepository.save(new UserPreferences(userId)));
    }

    /** Effective persistence threshold for this user and signal type, clamped to a sane range. */
    public int effectivePersistThreshold(UUID userId, SignalType type, int basePersistThreshold) {
        UserPreferences prefs = getOrCreate(userId);
        int delta = switch (type) {
            case DECOUPLING -> prefs.getDecouplingThresholdDelta();
            case SILENCE -> prefs.getSilenceThresholdDelta();
            case HISTORICAL_ABNORMALITY -> prefs.getAbnormalityThresholdDelta();
        };
        int effective = basePersistThreshold + delta;
        return Math.max(BASE_PERSIST_THRESHOLD_FLOOR, Math.min(BASE_PERSIST_THRESHOLD_CEILING, effective));
    }

    @Transactional
    public UserPreferences recalibrate(UUID userId) {
        UserPreferences prefs = getOrCreate(userId);
        List<SignalLedgerEntry> allEntries = ledgerRepository.findByUserIdOrderByLastDetectedAtDesc(userId);

        for (SignalType type : SignalType.values()) {
            List<SignalLedgerEntry> closed = allEntries.stream()
                    .filter(e -> e.getSignalType().equals(type.name()))
                    .filter(e -> e.isAcknowledged() || e.isDismissed())
                    .toList();

            long dismissedCount = closed.stream().filter(SignalLedgerEntry::isDismissed).count();
            long total = closed.size();
            if (total < MIN_SAMPLES) {
                continue; // not enough signal to adapt on yet
            }

            double dismissRate = (double) dismissedCount / total;
            int currentDelta = currentDelta(prefs, type);

            if (dismissRate > HIGH_DISMISS_RATE) {
                applyDelta(prefs, type, Math.min(MAX_DELTA, currentDelta + ADJUSTMENT_STEP));
            } else if (dismissRate < LOW_DISMISS_RATE) {
                applyDelta(prefs, type, Math.max(MIN_DELTA, currentDelta - ADJUSTMENT_STEP));
            }
        }

        return preferencesRepository.save(prefs);
    }

    private int currentDelta(UserPreferences prefs, SignalType type) {
        return switch (type) {
            case DECOUPLING -> prefs.getDecouplingThresholdDelta();
            case SILENCE -> prefs.getSilenceThresholdDelta();
            case HISTORICAL_ABNORMALITY -> prefs.getAbnormalityThresholdDelta();
        };
    }

    private void applyDelta(UserPreferences prefs, SignalType type, int newDelta) {
        switch (type) {
            case DECOUPLING -> prefs.setDecouplingThresholdDelta(newDelta);
            case SILENCE -> prefs.setSilenceThresholdDelta(newDelta);
            case HISTORICAL_ABNORMALITY -> prefs.setAbnormalityThresholdDelta(newDelta);
        }
    }
}
