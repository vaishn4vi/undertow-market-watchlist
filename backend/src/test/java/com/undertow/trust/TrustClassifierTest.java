package com.undertow.trust;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.undertow.trust.model.TrustStatus;
import com.undertow.trust.service.TrustClassifier;

class TrustClassifierTest {

    private final LocalDate today = LocalDate.of(2026, 3, 13);

    @Test
    void snapshotFromTodayIsLive() {
        assertThat(TrustClassifier.classifyFreshness(today, today, 1)).isEqualTo(TrustStatus.LIVE);
    }

    @Test
    void snapshotFromTheFutureIsTreatedAsLiveDefensively() {
        // Shouldn't happen in practice, but must not crash or misclassify.
        assertThat(TrustClassifier.classifyFreshness(today.plusDays(1), today, 1)).isEqualTo(TrustStatus.LIVE);
    }

    @Test
    void gapExactlyAtDelayedThresholdIsDelayedInclusive() {
        LocalDate snapshot = today.minusDays(1);
        assertThat(TrustClassifier.classifyFreshness(snapshot, today, 1)).isEqualTo(TrustStatus.DELAYED);
    }

    @Test
    void gapOneDayPastDelayedThresholdIsStale() {
        LocalDate snapshot = today.minusDays(2);
        assertThat(TrustClassifier.classifyFreshness(snapshot, today, 1)).isEqualTo(TrustStatus.STALE);
    }

    @Test
    void staleHasNoUpperBound() {
        LocalDate snapshot = today.minusDays(30);
        assertThat(TrustClassifier.classifyFreshness(snapshot, today, 1)).isEqualTo(TrustStatus.STALE);
    }

    @Test
    void higherThresholdShiftsTheDelayedWindow() {
        LocalDate snapshot = today.minusDays(3);
        assertThat(TrustClassifier.classifyFreshness(snapshot, today, 3)).isEqualTo(TrustStatus.DELAYED);
        assertThat(TrustClassifier.classifyFreshness(snapshot, today, 2)).isEqualTo(TrustStatus.STALE);
    }

    @Test
    void gapDaysNeverReturnsNegative() {
        assertThat(TrustClassifier.gapDays(today.plusDays(5), today)).isEqualTo(0);
        assertThat(TrustClassifier.gapDays(today.minusDays(5), today)).isEqualTo(5);
    }
}
