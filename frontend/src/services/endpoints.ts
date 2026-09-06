import { api } from "./api";
import type {
  Watchlist,
  WatchlistItem,
  SymbolSearchResult,
  MarketSnapshot,
  MarketHistoryPoint,
  SignalEvent,
  SignalEvidence,
  DetectionResponse,
  TrustAssessment,
  DataTrustOverview,
  LedgerEntry,
  AttentionDebt,
  DebtHistoryPoint,
  ReconciliationResult,
  BacktestResult,
  UserPreferences,
  AuthResponse,
} from "../types";

// ---------- Auth ----------
export const authApi = {
  signup: (email: string, password: string, displayName?: string) =>
    api.post<AuthResponse>("/auth/signup", { email, password, displayName }),
  login: (email: string, password: string) => api.post<AuthResponse>("/auth/login", { email, password }),
  logout: () => api.post<void>("/auth/logout"),
  me: () => api.get<{ email: string; displayName: string }>("/auth/me"),
};

// ---------- Watchlist ----------
export const watchlistApi = {
  list: () => api.get<Watchlist[]>("/watchlists"),
  create: (name: string) => api.post<Watchlist>("/watchlists", { name }),
  rename: (id: string, name: string) => api.patch<Watchlist>(`/watchlists/${id}`, { name }),
  remove: (id: string) => api.delete<void>(`/watchlists/${id}`),
  items: (id: string) => api.get<WatchlistItem[]>(`/watchlists/${id}/items`),
  addItem: (id: string, symbol: string) => api.post<WatchlistItem>(`/watchlists/${id}/items`, { symbol }),
  removeItem: (id: string, itemId: string) => api.delete<void>(`/watchlists/${id}/items/${itemId}`),
  searchSymbols: (q: string) => api.get<SymbolSearchResult[]>(`/symbols/search?q=${encodeURIComponent(q)}`),
};

// ---------- Market ----------
export const marketApi = {
  snapshots: (symbols: string[]) =>
    api.get<MarketSnapshot[]>(`/market/snapshots?symbols=${symbols.map(encodeURIComponent).join(",")}`),
  history: (symbol: string, range: "7d" | "30d" | "90d" = "30d") =>
    api.get<MarketHistoryPoint[]>(`/market/symbols/${symbol}/history?range=${range}`),
  demoReset: () => api.post<{ clock: string }>("/market/demo/reset"),
  demoAdvance: (days = 1) => api.post<{ clock: string }>(`/market/demo/advance?days=${days}`),
  demoFastForward: () => api.post<{ clock: string }>("/market/demo/fast-forward"),
};

// ---------- Signals ----------
export const signalsApi = {
  forSymbol: (symbol: string) => api.get<SignalEvent[]>(`/signals/symbols/${symbol}`),
  detect: (symbol: string) => api.post<DetectionResponse>(`/signals/symbols/${symbol}/detect`),
  evidence: (signalEventId: string) => api.get<SignalEvidence>(`/signals/${signalEventId}/evidence`),
};

// ---------- Trust ----------
export const trustApi = {
  forSymbol: (symbol: string) => api.get<TrustAssessment>(`/trust/symbols/${symbol}`),
  overview: () => api.get<DataTrustOverview>("/trust/overview"),
};

// ---------- Attention ----------
export const attentionApi = {
  debt: () => api.get<AttentionDebt>("/attention/debt"),
  debtHistory: () => api.get<DebtHistoryPoint[]>("/attention/debt/history"),
  ledger: () => api.get<LedgerEntry[]>("/attention/ledger"),
  sync: (symbol: string) => api.post<LedgerEntry[]>(`/attention/ledger/sync/${symbol}`),
  acknowledge: (entryId: string) => api.post<LedgerEntry>(`/attention/ledger/${entryId}/acknowledge`),
  dismiss: (entryId: string) => api.post<LedgerEntry>(`/attention/ledger/${entryId}/dismiss`),
  keepWatching: (entryId: string) => api.post<LedgerEntry>(`/attention/ledger/${entryId}/keep-watching`),
};

// ---------- Reconciliation ----------
export const reconciliationApi = {
  checkin: (requestId: string) => api.post<ReconciliationResult>("/reconciliation/checkin", { requestId }),
};

// ---------- Backtest ----------
export const backtestApi = {
  replay: (rangeDays: 7 | 30 | 90) => api.post<BacktestResult>(`/backtest/replay?rangeDays=${rangeDays}`),
};

// ---------- Preferences ----------
export const preferencesApi = {
  get: () => api.get<UserPreferences>("/preferences"),
};
