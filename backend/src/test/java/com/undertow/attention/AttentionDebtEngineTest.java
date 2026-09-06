package com.undertow.attention;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.undertow.attention.model.DebtBand;
import com.undertow.attention.model.DebtTrajectory;
import com.undertow.attention.service.AttentionDebtEngine;
import com.undertow.attention.service.AttentionDebtEngine.EntryWeight;

class AttentionDebtEngineTest {

    @Test
    void noOpenSignalsMeansZeroDebt() {
        var result = AttentionDebtEngine.compute(List.of(), null, 6.0);
        assertThat(result.normalizedDebt()).isEqualTo(0.0);
        assertThat(result.band()).isEqualTo(DebtBand.LOW);
    }

    @Test
    void moreConcurrentHighSeveritySignalsMeansHigherDebt() {
        var oneSignal = AttentionDebtEngine.compute(List.of(new EntryWeight(90, 1.0, 0)), null, 6.0);
        var threeSignals = AttentionDebtEngine.compute(
                List.of(new EntryWeight(90, 1.0, 2), new EntryWeight(88, 1.0, 2), new EntryWeight(92, 1.0, 2)),
                null, 6.0);
        assertThat(threeSignals.normalizedDebt()).isGreaterThan(oneSignal.normalizedDebt());
    }

    @Test
    void debtNeverExceedsOneHundredOrGoesNegative() {
        var extreme = AttentionDebtEngine.compute(
                List.of(new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5),
                        new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5),
                        new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5), new EntryWeight(100, 1.0, 5),
                        new EntryWeight(100, 1.0, 5)),
                null, 6.0);
        assertThat(extreme.normalizedDebt()).isLessThanOrEqualTo(100.0).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void trajectoryDivergesWhenDebtRisesByFiveOrMore() {
        var result = AttentionDebtEngine.compute(List.of(new EntryWeight(90, 1.0, 2)), 10.0, 6.0);
        assertThat(result.trajectory()).isEqualTo(DebtTrajectory.DIVERGING);
    }

    @Test
    void trajectoryConvergesWhenDebtFallsByFiveOrMore() {
        var result = AttentionDebtEngine.compute(List.of(new EntryWeight(10, 1.0, 0)), 80.0, 6.0);
        assertThat(result.trajectory()).isEqualTo(DebtTrajectory.CONVERGING);
    }

    @Test
    void trajectoryStableWithinFivePointBand() {
        var result = AttentionDebtEngine.compute(List.of(new EntryWeight(50, 1.0, 1)), null, 6.0);
        // Force a previous value close to the computed one.
        double computed = result.normalizedDebt();
        var stable = AttentionDebtEngine.compute(List.of(new EntryWeight(50, 1.0, 1)), computed + 2, 6.0);
        assertThat(stable.trajectory()).isEqualTo(DebtTrajectory.STABLE);
    }

    @Test
    void firstEverComputationHasStableTrajectory() {
        var result = AttentionDebtEngine.compute(List.of(new EntryWeight(90, 1.0, 2)), null, 6.0);
        assertThat(result.trajectory()).isEqualTo(DebtTrajectory.STABLE);
    }

    @Test
    void persistenceMultiplierIncreasesWeightUpToCap() {
        EntryWeight noPersist = new EntryWeight(80, 1.0, 0);
        EntryWeight somePersist = new EntryWeight(80, 1.0, 3);
        EntryWeight capped = new EntryWeight(80, 1.0, 100); // way beyond cap
        assertThat(somePersist.weight()).isGreaterThan(noPersist.weight());
        assertThat(capped.weight()).isEqualTo(new EntryWeight(80, 1.0, 5).weight()); // both hit the 1.5x cap
    }

    @Test
    void bandBoundariesAreInclusiveAtTheUpperEdge() {
        assertThat(DebtBand.fromScore(30.0)).isEqualTo(DebtBand.LOW);
        assertThat(DebtBand.fromScore(30.1)).isEqualTo(DebtBand.MODERATE);
        assertThat(DebtBand.fromScore(60.0)).isEqualTo(DebtBand.MODERATE);
        assertThat(DebtBand.fromScore(60.1)).isEqualTo(DebtBand.HIGH);
        assertThat(DebtBand.fromScore(80.0)).isEqualTo(DebtBand.HIGH);
        assertThat(DebtBand.fromScore(80.1)).isEqualTo(DebtBand.OVERLOADED);
    }
}
