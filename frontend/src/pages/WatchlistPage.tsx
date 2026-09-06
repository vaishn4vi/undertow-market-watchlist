import { useState, useMemo } from "react";
import { Trash2 } from "lucide-react";
import { Card } from "../components/Card";
import { WatchlistRow } from "../components/WatchlistRow";
import { LoadingState, ErrorState, EmptyState } from "../components/StateViews";
import {
  useWatchlists,
  useWatchlistItems,
  useCreateWatchlist,
  useDeleteWatchlist,
  useAddWatchlistItem,
  useRemoveWatchlistItem,
  useSymbolSearch,
  useMarketSnapshots,
  useLedger,
  useSyncSymbol,
} from "../hooks/useApi";
import { DEMO_WATCHLIST_NAME } from "../hooks/useDemoScenario";
import type { MarketSnapshot } from "../types";

export function WatchlistPage() {
  const { data: watchlists, isLoading: watchlistsLoading, isError: watchlistsError, refetch } = useWatchlists();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [newListName, setNewListName] = useState("");
  const [showNewList, setShowNewList] = useState(false);
  const createWatchlist = useCreateWatchlist();
  const deleteWatchlist = useDeleteWatchlist();

  const active = activeId ?? watchlists?.[0]?.id ?? null;

  const handleDelete = (id: string) => {
    if (!window.confirm("Delete this watchlist?")) return;
    deleteWatchlist.mutate(id, {
      onSuccess: () => {
        // If the deleted list was the one currently selected, fall back to
        // "no explicit selection" - the `active` derivation above then
        // picks whatever the first remaining watchlist is once the list
        // refetches, rather than pointing at an id that no longer exists.
        if (activeId === id) setActiveId(null);
      },
    });
  };

  if (watchlistsLoading) return <LoadingState label={"Loading watchlists\u2026"} />;
  if (watchlistsError) return <ErrorState message="Couldn't load your watchlists." onRetry={() => refetch()} />;

  return (
    <div className="flex flex-col gap-4">
      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
          Watchlist
        </div>
        <h1 className="sr-only">Watchlist</h1>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          {watchlists?.map((w) => {
            const isDemo = w.name === DEMO_WATCHLIST_NAME;
            return (
              <div
                key={w.id}
                className={
                  "group flex items-center rounded-[var(--radius-pill)] transition-colors " +
                  (active === w.id ? "bg-[var(--color-accent-soft)]" : "hover:bg-[var(--color-surface-sunken)]")
                }
              >
                <button
                  type="button"
                  onClick={() => setActiveId(w.id)}
                  className={
                    "rounded-l-[var(--radius-pill)] py-1.5 pl-3 text-[var(--text-small)] font-medium " +
                    (active === w.id ? "text-[var(--color-accent-strong)]" : "text-[var(--color-text-muted)]") +
                    (isDemo ? " rounded-r-[var(--radius-pill)] pr-3" : " pr-1.5")
                  }
                >
                  {w.name} <span className="text-[var(--color-text-faint)]">({w.itemCount})</span>
                </button>
                {!isDemo && (
                  <button
                    type="button"
                    onClick={() => handleDelete(w.id)}
                    aria-label={`Delete ${w.name}`}
                    title="Delete watchlist"
                    className="rounded-r-[var(--radius-pill)] py-1.5 pl-1 pr-2.5 text-[var(--color-text-faint)] opacity-0 transition-opacity hover:text-[var(--color-negative)] focus-visible:opacity-100 group-hover:opacity-100"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            );
          })}
        </div>

        {showNewList ? (
          <form
            className="flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (!newListName.trim()) return;
              createWatchlist.mutate(newListName.trim(), {
                onSuccess: (w) => {
                  setActiveId(w.id);
                  setNewListName("");
                  setShowNewList(false);
                },
              });
            }}
          >
            <input
              autoFocus
              value={newListName}
              onChange={(e) => setNewListName(e.target.value)}
              placeholder="Watchlist name"
              className="rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-2 py-1.5 text-[var(--text-small)] outline-none focus:border-[var(--color-accent)]"
            />
            <button type="submit" className="rounded-[var(--radius-sm)] bg-[var(--color-accent)] px-3 py-1.5 text-[var(--text-small)] font-medium text-white">
              Create
            </button>
          </form>
        ) : (
          <button
            type="button"
            onClick={() => setShowNewList(true)}
            className="surface-interactive rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-1.5 text-[var(--text-small)] font-medium text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent-strong)]"
          >
            + New watchlist
          </button>
        )}
      </div>

      {!active && (
        <Card>
          <EmptyState>Create a watchlist to start tracking stocks.</EmptyState>
        </Card>
      )}

      {active && <WatchlistDetail watchlistId={active} watchlistName={watchlists?.find((w) => w.id === active)?.name ?? ""} />}
    </div>
  );
}

function WatchlistDetail({ watchlistId, watchlistName }: { watchlistId: string; watchlistName: string }) {
  const { data: items, isLoading, isError, refetch } = useWatchlistItems(watchlistId);
  const removeItem = useRemoveWatchlistItem(watchlistId);
  const symbols = useMemo(() => items?.map((i) => i.symbol) ?? [], [items]);
  const { data: snapshots } = useMarketSnapshots(symbols);
  const { data: ledger } = useLedger();
  const syncSymbol = useSyncSymbol();

  const snapshotBySymbol = useMemo(() => {
    const map = new Map<string, MarketSnapshot>();
    snapshots?.forEach((s) => map.set(s.symbol, s));
    return map;
  }, [snapshots]);

  const handleRefreshSignals = () => {
    symbols.forEach((s) => syncSymbol.mutate(s));
  };

  const loudCount = useMemo(() => {
    if (!items || !ledger) return 0;
    const symbolSet = new Set(items.map((i) => i.symbol));
    return ledger.filter((e) => symbolSet.has(e.symbol) && e.status !== "RESOLVED").length;
  }, [items, ledger]);

  return (
    <Card className="p-0">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border)] px-4 py-3">
        <div className="flex items-center gap-2">
          <span className="text-[var(--text-body)] font-medium">{watchlistName}</span>
          {loudCount > 0 && (
            <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">
              {loudCount} needing attention
            </span>
          )}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <AddStockForm watchlistId={watchlistId} />
          <button
            type="button"
            onClick={handleRefreshSignals}
            disabled={symbols.length === 0 || syncSymbol.isPending}
            className="surface-interactive rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-2.5 py-1.5 text-[var(--text-small)] font-medium text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent-strong)] disabled:opacity-40"
          >
            {syncSymbol.isPending ? "Refreshing\u2026" : "Refresh signals"}
          </button>
        </div>
      </div>

      {isLoading && <LoadingState label={"Loading items\u2026"} />}
      {isError && <ErrorState message="Couldn't load this watchlist." onRetry={() => refetch()} />}

      {!isLoading && !isError && (
        <div className="flex flex-col gap-2 p-3">
          {items?.length === 0 && (
            <div className="py-10">
              <EmptyState>No stocks yet. Add one above to start tracking it.</EmptyState>
            </div>
          )}
          {items?.map((item) => {
            const entries = ledger?.filter((l) => l.symbol === item.symbol) ?? [];
            return (
              <WatchlistRow
                key={item.id}
                item={item}
                snapshot={snapshotBySymbol.get(item.symbol)}
                ledgerEntries={entries}
                onRemove={() => removeItem.mutate(item.id)}
              />
            );
          })}
        </div>
      )}
    </Card>
  );
}

function AddStockForm({ watchlistId }: { watchlistId: string }) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const { data: results } = useSymbolSearch(query);
  const addItem = useAddWatchlistItem(watchlistId);

  return (
    <div className="relative">
      <input
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        placeholder={"Add stock\u2026"}
        className="w-40 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-2.5 py-1.5 text-[var(--text-small)] outline-none focus:border-[var(--color-accent)]"
      />
      {open && query && results && results.length > 0 && (
        <div className="absolute right-0 z-10 mt-1 w-56 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] py-1 shadow-[var(--shadow-md)]">
          {results.slice(0, 8).map((r) => (
            <button
              key={r.ticker}
              type="button"
              onClick={() => {
                addItem.mutate(r.ticker);
                setQuery("");
                setOpen(false);
              }}
              className="flex w-full items-center justify-between px-3 py-1.5 text-left text-[var(--text-small)] hover:bg-[var(--color-surface-sunken)]"
            >
              <span className="font-mono font-medium">{r.ticker}</span>
              <span className="truncate text-[var(--text-micro)] text-[var(--color-text-faint)]">{r.sector}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
