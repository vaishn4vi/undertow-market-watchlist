package com.undertow.attention.service;

import java.util.List;

import com.undertow.attention.model.DebtBand;
import com.undertow.attention.model.DebtTrajectory;

/**
 * Computes the Attention Debt index from a user's currently open (non-resolved)
 * ledger entries. Deliberately a snapshot-based model (sum of current
 * unresolved load) rather than a strict running accumulator - simpler,
 * equally deterministic, and avoids needing to replay full event history to
 * recompute a score, at the cost of not being a literal delta-integral. The
 * trajectory (rising/falling) is what captures the "debt accumulating faster
 * than it's clearing" signal the product actually needs.
 */
public final class AttentionDebtEngine {

    private AttentionDebtEngine() {
    }

    public record EntryWeight(int severity, double confidenceFactor, int persistenceCount) {
        public double weight() {
            double persistenceMultiplier = Math.min(1.0 + 0.1 * persistenceCount, 1.5);
            return (severity / 100.0) * confidenceFactor * persistenceMultiplier;
        }
    }

    public record DebtResult(double rawDebt, double normalizedDebt, DebtBand band, DebtTrajectory trajectory) {
    }

    /**
     * @param weights             one entry per currently-open ledger entry
     * @param previousNormalizedDebt the last computed score for this user, or
     *                            null if this is the first computation ever
     * @param normalizationK      calibration constant (default 6.0) - chosen
     *                            so a handful of concurrent high-severity
     *                            signals saturates toward the OVERLOADED band
     *                            without needing an arbitrary hard clamp
     */
    public static DebtResult compute(List<EntryWeight> weights, Double previousNormalizedDebt, double normalizationK) {
        double rawDebt = weights.stream().mapToDouble(EntryWeight::weight).sum();
        double normalizedDebt = 100.0 * (1.0 - Math.exp(-rawDebt / normalizationK));
        DebtBand band = DebtBand.fromScore(normalizedDebt);

        DebtTrajectory trajectory;
        if (previousNormalizedDebt == null) {
            trajectory = DebtTrajectory.STABLE;
        } else {
            double delta = normalizedDebt - previousNormalizedDebt;
            if (delta <= -5) trajectory = DebtTrajectory.CONVERGING;
            else if (delta >= 5) trajectory = DebtTrajectory.DIVERGING;
            else trajectory = DebtTrajectory.STABLE;
        }

        return new DebtResult(rawDebt, normalizedDebt, band, trajectory);
    }
}
