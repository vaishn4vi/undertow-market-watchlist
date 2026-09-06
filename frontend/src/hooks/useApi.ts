import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  watchlistApi,
  marketApi,
  signalsApi,
  trustApi,
  attentionApi,
  reconciliationApi,
  backtestApi,
  preferencesApi,
} from "../services/endpoints";

// ---------- Watchlist ----------
export function useWatchlists() {
  return useQuery({ queryKey: ["watchlists"], queryFn: watchlistApi.list });
}

export function useWatchlistItems(watchlistId: string | undefined) {
  return useQuery({
    queryKey: ["watchlist-items", watchlistId],
    queryFn: () => watchlistApi.items(watchlistId as string),
    enabled: !!watchlistId,
  });
}

export function useCreateWatchlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => watchlistApi.create(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["watchlists"] }),
  });
}

export function useAddWatchlistItem(watchlistId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (symbol: string) => watchlistApi.addItem(watchlistId, symbol),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["watchlist-items", watchlistId] });
      qc.invalidateQueries({ queryKey: ["watchlists"] });
    },
  });
}

export function useRemoveWatchlistItem(watchlistId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (itemId: string) => watchlistApi.removeItem(watchlistId, itemId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["watchlist-items", watchlistId] });
      qc.invalidateQueries({ queryKey: ["watchlists"] });
    },
  });
}

export function useDeleteWatchlist() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (watchlistId: string) => watchlistApi.remove(watchlistId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["watchlists"] }),
  });
}

export function useSymbolSearch(query: string) {
  return useQuery({
    queryKey: ["symbol-search", query],
    queryFn: () => watchlistApi.searchSymbols(query),
    enabled: query.length > 0,
  });
}

// ---------- Market ----------
export function useMarketSnapshots(symbols: string[]) {
  return useQuery({
    queryKey: ["market-snapshots", symbols.join(",")],
    queryFn: () => marketApi.snapshots(symbols),
    enabled: symbols.length > 0,
  });
}

export function useMarketHistory(symbol: string | undefined, range: "7d" | "30d" | "90d" = "30d") {
  return useQuery({
    queryKey: ["market-history", symbol, range],
    queryFn: () => marketApi.history(symbol as string, range),
    enabled: !!symbol,
  });
}

// ---------- Signals ----------
export function useSignalsForSymbol(symbol: string | undefined) {
  return useQuery({
    queryKey: ["signals", symbol],
    queryFn: () => signalsApi.forSymbol(symbol as string),
    enabled: !!symbol,
  });
}

export function useSignalEvidence(signalEventId: string | null | undefined) {
  return useQuery({
    queryKey: ["signal-evidence", signalEventId],
    queryFn: () => signalsApi.evidence(signalEventId as string),
    enabled: !!signalEventId,
  });
}

// ---------- Trust ----------
export function useTrust(symbol: string | undefined) {
  return useQuery({
    queryKey: ["trust", symbol],
    queryFn: () => trustApi.forSymbol(symbol as string),
    enabled: !!symbol,
  });
}

export function useDataTrustOverview() {
  return useQuery({
    queryKey: ["trust-overview"],
    queryFn: trustApi.overview,
    // Trust status doesn't need to be second-by-second live; refetch
    // periodically so the global indicator stays reasonably current
    // without hammering the endpoint.
    refetchInterval: 60_000,
  });
}

// ---------- Attention ----------
export function useAttentionDebt() {
  return useQuery({ queryKey: ["attention-debt"], queryFn: attentionApi.debt });
}

export function useDebtHistory() {
  return useQuery({ queryKey: ["debt-history"], queryFn: attentionApi.debtHistory });
}

export function useLedger() {
  return useQuery({ queryKey: ["ledger"], queryFn: attentionApi.ledger });
}

export function useSyncSymbol() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (symbol: string) => attentionApi.sync(symbol),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
    },
  });
}

export function useAcknowledge() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) => attentionApi.acknowledge(entryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
    },
  });
}

export function useDismiss() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) => attentionApi.dismiss(entryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
    },
  });
}

export function useKeepWatching() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entryId: string) => attentionApi.keepWatching(entryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
    },
  });
}

// ---------- Reconciliation ----------
export function useCheckin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) => reconciliationApi.checkin(requestId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
      qc.invalidateQueries({ queryKey: ["debt-history"] });
    },
  });
}

// ---------- Backtest ----------
export function useBacktest() {
  return useMutation({ mutationFn: (rangeDays: 7 | 30 | 90) => backtestApi.replay(rangeDays) });
}

// ---------- Preferences ----------
export function usePreferences() {
  return useQuery({ queryKey: ["preferences"], queryFn: preferencesApi.get });
}
