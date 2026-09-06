package com.undertow.attention;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.undertow.attention.model.LedgerStatus;
import com.undertow.attention.service.LedgerHysteresis;
import com.undertow.attention.service.LedgerHysteresis.EntryState;

class LedgerHysteresisTest {

    private static final int PERSIST = 70;
    private static final int RESOLVE = 50;
    private static final int WORSEN_DELTA = 15;

    @Test
    void creationAlwaysStartsAtNew() {
        EntryState s = LedgerHysteresis.create(82, PERSIST, RESOLVE);
        assertThat(s.status()).isEqualTo(LedgerStatus.NEW);
        assertThat(s.currentSeverity()).isEqualTo(82);
        assertThat(s.maxSeverity()).isEqualTo(82);
    }

    @Test
    void twoConsecutiveHighReadingsPromoteToPersisted() {
        EntryState s = LedgerHysteresis.create(82, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 78, PERSIST, RESOLVE, WORSEN_DELTA);
        assertThat(s.status()).isEqualTo(LedgerStatus.PERSISTED);
        assertThat(s.persistenceCount()).isEqualTo(2);
    }

    @Test
    void oneHighReadingIsNotYetPersisted() {
        EntryState s = LedgerHysteresis.create(82, PERSIST, RESOLVE);
        // still NEW after creation; the "one follow-up" case is tested via
        // the second update landing on ACTIVE if it doesn't reach 2 yet -
        // simulate a dip then a rise so persistenceCount resets to 1.
        s = LedgerHysteresis.update(s, 40, PERSIST, RESOLVE, WORSEN_DELTA); // dip
        s = LedgerHysteresis.update(s, 75, PERSIST, RESOLVE, WORSEN_DELTA); // one high reading only
        assertThat(s.status()).isNotEqualTo(LedgerStatus.PERSISTED);
        assertThat(s.persistenceCount()).isEqualTo(1);
    }

    @Test
    void gradualFallResolvesAfterTwoConsecutiveLowReadings() {
        EntryState s = LedgerHysteresis.create(82, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 78, PERSIST, RESOLVE, WORSEN_DELTA); // PERSISTED
        s = LedgerHysteresis.update(s, 55, PERSIST, RESOLVE, WORSEN_DELTA); // dead zone
        assertThat(s.status()).isEqualTo(LedgerStatus.ACTIVE);
        s = LedgerHysteresis.update(s, 45, PERSIST, RESOLVE, WORSEN_DELTA); // first low
        assertThat(s.status()).isEqualTo(LedgerStatus.ACTIVE);
        s = LedgerHysteresis.update(s, 40, PERSIST, RESOLVE, WORSEN_DELTA); // second low
        assertThat(s.status()).isEqualTo(LedgerStatus.RESOLVED);
    }

    @Test
    void meanRevertingNoiseResolvesQuicklyWithoutEverPersisting() {
        EntryState s = LedgerHysteresis.create(71, PERSIST, RESOLVE); // barely triggered
        s = LedgerHysteresis.update(s, 30, PERSIST, RESOLVE, WORSEN_DELTA);
        s = LedgerHysteresis.update(s, 25, PERSIST, RESOLVE, WORSEN_DELTA);

        assertThat(s.status()).isEqualTo(LedgerStatus.RESOLVED);
        // never accumulated a persistence streak along the way
    }

    @Test
    void bigDeltaTriggersWorsenedRegardlessOfAbsoluteLevel() {
        EntryState s = LedgerHysteresis.create(60, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 78, PERSIST, RESOLVE, WORSEN_DELTA); // +18
        assertThat(s.status()).isEqualTo(LedgerStatus.WORSENED);
        assertThat(s.worsenedFlag()).isTrue();
    }

    @Test
    void crossingPersistThresholdTriggersWorsenedEvenWithSmallDelta() {
        EntryState s = LedgerHysteresis.create(60, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 74, PERSIST, RESOLVE, WORSEN_DELTA); // +14, but crosses 70
        assertThat(s.status()).isEqualTo(LedgerStatus.WORSENED);
    }

    @Test
    void smallDeltaStayingBelowThresholdDoesNotWorsen() {
        EntryState s = LedgerHysteresis.create(55, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 65, PERSIST, RESOLVE, WORSEN_DELTA); // +10, stays below 70
        assertThat(s.status()).isNotEqualTo(LedgerStatus.WORSENED);
        assertThat(s.worsenedFlag()).isFalse();
    }

    @Test
    void worsenedFlagIsAPermanentHistoricalMarkerEvenAfterResolution() {
        EntryState s = LedgerHysteresis.create(60, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 78, PERSIST, RESOLVE, WORSEN_DELTA); // WORSENED
        s = LedgerHysteresis.update(s, 80, PERSIST, RESOLVE, WORSEN_DELTA); // PERSISTED, worsenedFlag stays true
        s = LedgerHysteresis.update(s, 35, PERSIST, RESOLVE, WORSEN_DELTA);
        s = LedgerHysteresis.update(s, 30, PERSIST, RESOLVE, WORSEN_DELTA); // RESOLVED

        assertThat(s.status()).isEqualTo(LedgerStatus.RESOLVED);
        assertThat(s.worsenedFlag()).isTrue();
    }

    @Test
    void deadZoneResetsBothStreaksPreventingFlapping() {
        EntryState s = LedgerHysteresis.create(72, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 71, PERSIST, RESOLVE, WORSEN_DELTA);
        assertThat(s.persistenceCount()).isEqualTo(2);

        s = LedgerHysteresis.update(s, 60, PERSIST, RESOLVE, WORSEN_DELTA); // dead zone: 50 < 60 < 70
        assertThat(s.persistenceCount()).isEqualTo(0);
        assertThat(s.resolveStreak()).isEqualTo(0);

        s = LedgerHysteresis.update(s, 72, PERSIST, RESOLVE, WORSEN_DELTA);
        assertThat(s.persistenceCount()).isEqualTo(1); // restarted, not continuing from before the dip
    }

    @Test
    void resumeFromUnverifiedNeverAutoResolvesEvenWithLowSeverity() {
        EntryState beforeGap = LedgerHysteresis.create(85, PERSIST, RESOLVE);
        beforeGap = LedgerHysteresis.update(beforeGap, 88, PERSIST, RESOLVE, WORSEN_DELTA); // PERSISTED

        EntryState resumed = LedgerHysteresis.resumeFromUnverified(beforeGap.maxSeverity(), 20, PERSIST, RESOLVE);

        assertThat(resumed.status()).isEqualTo(LedgerStatus.ACTIVE); // not RESOLVED, despite severity=20
        assertThat(resumed.resolveStreak()).isEqualTo(1); // counts as the FIRST low reading, not the second
    }

    @Test
    void resumeFromUnverifiedNeverAutoEscalatesEvenWithHighSeverity() {
        EntryState beforeGap = LedgerHysteresis.create(50, PERSIST, RESOLVE);

        EntryState resumed = LedgerHysteresis.resumeFromUnverified(beforeGap.maxSeverity(), 99, PERSIST, RESOLVE);

        assertThat(resumed.status()).isEqualTo(LedgerStatus.ACTIVE); // not WORSENED, despite the huge jump
        assertThat(resumed.maxSeverity()).isEqualTo(99); // max tracking still updates correctly
    }

    @Test
    void persistThresholdIsInclusiveAtCreation() {
        EntryState s = LedgerHysteresis.create(70, PERSIST, RESOLVE);
        assertThat(s.persistenceCount()).isEqualTo(1);
    }

    @Test
    void resolveThresholdIsInclusiveAtCreation() {
        EntryState s = LedgerHysteresis.create(50, PERSIST, RESOLVE);
        assertThat(s.resolveStreak()).isEqualTo(1);
    }

    @Test
    void worsenDeltaThresholdIsInclusiveAtExactly15() {
        EntryState s = LedgerHysteresis.create(60, PERSIST, RESOLVE);
        s = LedgerHysteresis.update(s, 75, PERSIST, RESOLVE, WORSEN_DELTA); // exactly +15
        assertThat(s.status()).isEqualTo(LedgerStatus.WORSENED);
    }
}
