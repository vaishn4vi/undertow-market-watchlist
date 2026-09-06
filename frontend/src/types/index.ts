// Mirrors backend DTOs exactly (see backend/src/main/java/com/undertow/*/dto).

export type SignalType = "DECOUPLING" | "SILENCE" | "HISTORICAL_ABNORMALITY";

export interface AuthUser {
  email: string;
  displayName: string;
}

export interface AuthResponse extends AuthUser {
  token: string;
}


export type LedgerStatus =
  | "NEW"
  | "ACTIVE"
  | "PERSISTED"
  | "WORSENED"
  | "UNVERIFIED"
  | "RESOLVED";

export type TrustStatus =
  | "LIVE"
  | "DELAYED"
  | "STALE"
  | "UNAVAILABLE"
  | "CONFLICTING"
  | "UNKNOWN";

export type DebtBand = "LOW" | "MODERATE" | "HIGH" | "OVERLOADED";
export type DebtTrajectory = "CONVERGING" | "STABLE" | "DIVERGING";

export type ReconciliationOutcome =
  | "RESOLVED"
  | "STILL_ACTIVE"
  | "WORSENED"
  | "NEWLY_DETECTED"
  | "UNVERIFIABLE";

export interface Watchlist {
  id: string;
  name: string;
  position: number;
  itemCount: number;
}

export interface WatchlistItem {
  id: string;
  symbol: string;
  sector: string;
  position: number;
  addedAt: string;
}

export interface SymbolSearchResult {
  ticker: string;
  name: string;
  sector: string;
}

export interface MarketSnapshot {
  symbol: string;
  sector: string;
  price: number;
  returnPct: number;
  sectorReturnPct: number;
  peerBasketReturnPct: number;
  marketStatus: string;
  asOf: string;
  isCurrent: boolean;
}

export interface MarketHistoryPoint {
  asOf: string;
  price: number;
  returnPct: number;
  sectorReturnPct: number;
  peerBasketReturnPct: number;
}

export interface SignalEvent {
  id: string;
  symbol: string;
  type: SignalType;
  severity: number;
  confidence: number;
  detectedAt: string;
}

export interface SignalEvidence {
  stockReturn: number;
  sectorReturn: number;
  expectedReturn: number;
  deviation: number;
  historicalPercentile: number;
  extra: Record<string, number>;
}

export interface DetectionResponse {
  events: SignalEvent[];
  dataUnavailable: boolean;
  trustStatus: TrustStatus;
  confidence: number;
}

export interface TrustAssessment {
  status: TrustStatus;
  confidence: number;
  explanation: string;
}

export interface LedgerEntry {
  id: string;
  symbol: string;
  signalType: SignalType;
  status: LedgerStatus;
  firstDetectedAt: string;
  lastDetectedAt: string;
  previousSeverity: number | null;
  currentSeverity: number;
  maxSeverity: number;
  resolvedAt: string | null;
  verificationStatus: string;
  persistenceCount: number;
  resolveStreak: number;
  worsenedFlag: boolean;
  acknowledged: boolean;
  acknowledgedAt: string | null;
  latestSignalEventId: string | null;
  dismissed: boolean;
}

export interface AttentionDebt {
  rawDebt: number;
  normalizedDebt: number;
  band: DebtBand;
  trajectory: DebtTrajectory;
  explanation: string;
  topPriorities: LedgerEntry[];
  deferredCount: number;
}

export interface DebtHistoryPoint {
  computedAt: string;
  normalizedDebt: number;
  band: DebtBand;
  trajectory: DebtTrajectory;
}

export interface ReconciliationEvent {
  symbol: string;
  signalType: SignalType;
  outcome: ReconciliationOutcome;
  severityBefore: number | null;
  severityAfter: number | null;
  narrative: string;
}

export interface ReconciliationResult {
  checkinId: string;
  daysAway: number;
  previousCheckinAt: string | null;
  totalMeaningfulChanges: number;
  resolvedCount: number;
  activeCount: number;
  worsenedCount: number;
  unverifiableCount: number;
  newCount: number;
  events: ReconciliationEvent[];
  debtBefore: number;
  debtAfter: number;
  trajectory: DebtTrajectory;
}

export interface BacktestSymbolResult {
  symbol: string;
  detected: number;
  persisted: number;
  resolved: number;
  prematureAlerts: number;
  meanReversionRate: number;
  averageLifetimeDays: number;
}

export interface BacktestResult {
  rangeDays: number;
  from: string;
  to: string;
  totalDetected: number;
  totalPersisted: number;
  totalResolved: number;
  totalPremature: number;
  meanReversionRate: number;
  averageSignalLifetimeDays: number;
  bySymbol: BacktestSymbolResult[];
}

export interface DataTrustOverview {
  overallStatus: TrustStatus | "UNKNOWN";
  totalSymbols: number;
  distribution: Record<string, number>;
  asOf: string;
}

export interface UserPreferences {
  persistenceThreshold: number;
  decouplingThresholdDelta: number;
  silenceThresholdDelta: number;
  abnormalityThresholdDelta: number;
  notificationPref: string;
}
