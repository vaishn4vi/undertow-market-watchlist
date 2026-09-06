import type { LedgerStatus, TrustStatus, ReconciliationOutcome, DebtBand, SignalEvidence, SignalType } from "../types";

// Deterministic, template-based explanation from already-computed evidence.
// This is the ONLY place anything resembling "explanation" happens on the
// frontend, and it does no inference of its own - every number here comes
// straight from the backend's structured evidence (an explanation layer may
// only restate evidence the engine already produced).
export function explainSignal(type: SignalType | undefined, symbol: string, evidence: SignalEvidence): string {
  const stock = evidence.stockReturn.toFixed(2);
  const expected = evidence.expectedReturn.toFixed(2);
  const deviation = evidence.deviation.toFixed(2);
  const percentile = evidence.historicalPercentile.toFixed(0);

  switch (type) {
    case "DECOUPLING":
      return `${symbol} moved against its usual relationship with its sector today. Given recent history, its expected move was approximately ${expected}%, while it actually returned ${stock}% — a break of ${deviation} percentage points from what the sector relationship would predict.`;
    case "SILENCE":
      return `${symbol} barely participated in a meaningful sector move. Based on its recent relationship with the sector, we'd expect roughly ${expected}%; it returned only ${stock}%.`;
    case "HISTORICAL_ABNORMALITY":
      return `${symbol}'s move of ${stock}% today is unusual relative to its own recent history — larger than about ${percentile}% of its typical daily moves.`;
    default:
      return `${symbol} returned ${stock}% against an expected ${expected}%, a deviation of ${deviation} percentage points.`;
  }
}

export function ledgerStatusTone(status: LedgerStatus): "neutral" | "positive" | "negative" | "warning" | "accent" {
  switch (status) {
    case "RESOLVED":
      return "positive";
    case "WORSENED":
      return "negative";
    case "PERSISTED":
      return "warning";
    case "UNVERIFIED":
      return "neutral";
    case "NEW":
      return "accent";
    default:
      return "accent";
  }
}

export function ledgerStatusLabel(status: LedgerStatus): string {
  switch (status) {
    case "NEW":
      return "New";
    case "ACTIVE":
      return "Active";
    case "PERSISTED":
      return "Persisted";
    case "WORSENED":
      return "Worsened";
    case "UNVERIFIED":
      return "Unverified";
    case "RESOLVED":
      return "Resolved";
  }
}

export function trustTone(status: TrustStatus): "neutral" | "positive" | "negative" | "warning" | "accent" {
  switch (status) {
    case "LIVE":
      return "positive";
    case "DELAYED":
      return "warning";
    case "STALE":
      return "warning";
    case "CONFLICTING":
      return "negative";
    case "UNAVAILABLE":
      return "negative";
    default:
      return "neutral";
  }
}

export function signalTypeLabel(type: string): string {
  switch (type) {
    case "DECOUPLING":
      return "Decoupling";
    case "SILENCE":
      return "Silence";
    case "HISTORICAL_ABNORMALITY":
      return "Historical abnormality";
    default:
      return type;
  }
}

// Generic, signal-type-level description of what the pattern means -
// deliberately NOT specific to any instance's numbers (those come from the
// backend's structured evidence and are shown in the full evidence panel).
// This is just enough to preview "why it matters" on a priority card
// without an extra fetch per card.
export function signalTypeHint(type: string): string {
  switch (type) {
    case "DECOUPLING":
      return "Moving against its usual sector relationship";
    case "SILENCE":
      return "Expected movement \u2260 observed movement";
    case "HISTORICAL_ABNORMALITY":
      return "Move is unusual relative to its own history";
    default:
      return "Unusual market behavior detected";
  }
}

export function severityColor(severity: number): string {
  if (severity >= 70) return "var(--color-negative)";
  if (severity >= 40) return "var(--color-debt-moderate)";
  return "var(--color-text-muted)";
}

export interface DebtBreakdown {
  new: number;
  active: number;
  persisted: number;
  worsened: number;
  unverified: number;
  resolved: number;
}

export function countByStatus(statuses: LedgerStatus[]): DebtBreakdown {
  const counts: DebtBreakdown = { new: 0, active: 0, persisted: 0, worsened: 0, unverified: 0, resolved: 0 };
  for (const s of statuses) {
    if (s === "NEW") counts.new++;
    else if (s === "ACTIVE") counts.active++;
    else if (s === "PERSISTED") counts.persisted++;
    else if (s === "WORSENED") counts.worsened++;
    else if (s === "UNVERIFIED") counts.unverified++;
    else if (s === "RESOLVED") counts.resolved++;
  }
  return counts;
}

export function outcomeTone(outcome: ReconciliationOutcome): "neutral" | "positive" | "negative" | "warning" | "accent" {
  switch (outcome) {
    case "RESOLVED":
      return "positive";
    case "WORSENED":
      return "negative";
    case "NEWLY_DETECTED":
      return "accent";
    case "UNVERIFIABLE":
      return "neutral";
    case "STILL_ACTIVE":
      return "warning";
  }
}

export function outcomeSymbol(outcome: ReconciliationOutcome): string {
  switch (outcome) {
    case "RESOLVED":
      return "\u2713"; // check
    case "WORSENED":
      return "\u2191"; // up arrow
    case "NEWLY_DETECTED":
      return "+";
    case "UNVERIFIABLE":
      return "\u26A0"; // warning
    case "STILL_ACTIVE":
      return "\u25CF"; // dot
  }
}

export function trustStatusColor(status: string): string {
  switch (status) {
    case "LIVE":
      return "var(--color-positive)";
    case "DELAYED":
      return "var(--color-debt-moderate)";
    case "STALE":
      return "var(--color-debt-high)";
    case "CONFLICTING":
      return "var(--color-negative)";
    case "UNAVAILABLE":
      return "var(--color-debt-overloaded)";
    default:
      return "var(--color-text-faint)";
  }
}

export function trustStatusDescription(status: string): string {
  switch (status) {
    case "LIVE":
      return "Verified as of the latest close.";
    case "DELAYED":
      return "Source data is slightly behind the latest close.";
    case "STALE":
      return "Source data is old enough that signals may not reflect current conditions.";
    case "CONFLICTING":
      return "Two disagreeing observations were received; the first accepted value is in use.";
    case "UNAVAILABLE":
      return "No usable data. Previous values are being carried forward without re-evaluation.";
    default:
      return "No market data has ever been ingested for this symbol.";
  }
}
export function debtBandColor(band: DebtBand): string {
  switch (band) {
    case "LOW":
      return "var(--color-debt-low)";
    case "MODERATE":
      return "var(--color-debt-moderate)";
    case "HIGH":
      return "var(--color-debt-high)";
    case "OVERLOADED":
      return "var(--color-debt-overloaded)";
  }
}
