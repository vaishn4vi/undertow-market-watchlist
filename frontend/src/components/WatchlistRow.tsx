import { clsx } from "clsx";
import { LineChart, Line, ResponsiveContainer, YAxis } from "recharts";
import type { WatchlistItem, MarketSnapshot, LedgerEntry } from "../types";
import { StatusPill } from "./StatusPill";
import { useTrust, useMarketHistory } from "../hooks/useApi";
import { useEvidenceDrawer } from "../hooks/useEvidenceDrawer";
import {
  ledgerStatusLabel,
  ledgerStatusTone,
  trustTone,
  signalTypeLabel,
  severityColor,
} from "../utils/statusStyles";

export function WatchlistRow({
  item,
  snapshot,
  ledgerEntries,
  onRemove,
}: {
  item: WatchlistItem;
  snapshot: MarketSnapshot | undefined;
  ledgerEntries: LedgerEntry[];
  onRemove: () => void;
}) {
  const { data: trust } = useTrust(item.symbol);
  const { data: history } = useMarketHistory(item.symbol, "30d");
  const { open } = useEvidenceDrawer();
  const topEntry = ledgerEntries.filter((e) => e.status !== "RESOLVED").sort((a, b) => b.currentSeverity - a.currentSeverity)[0];
  const returnPct = snapshot?.returnPct ?? null;

  // "Loud" = something unresolved is contributing to the attention story
  // for this symbol. Everything else stays visually quiet on purpose - the
  // watchlist should read as calm by default, with attention pulled only
  // where it's warranted.
  const isLoud = !!topEntry;
  const accent = topEntry ? severityColor(topEntry.currentSeverity) : undefined;

  const sparkData = (history ?? []).map((h, i) => ({ i, v: h.price }));
  const sparkColor = returnPct == null ? "var(--color-text-faint)" : returnPct >= 0 ? "var(--color-positive)" : "var(--color-negative)";

  const handleOpen = () => {
    if (!topEntry) return;
    open({
      signalEventId: topEntry.latestSignalEventId ?? topEntry.id,
      symbol: topEntry.symbol,
      type: topEntry.signalType,
      severity: topEntry.currentSeverity,
      status: topEntry.status,
      persistenceCount: topEntry.persistenceCount,
    });
  };

  return (
    <div
      role={topEntry ? "button" : undefined}
      tabIndex={topEntry ? 0 : undefined}
      onClick={topEntry ? handleOpen : undefined}
      onKeyDown={
        topEntry
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                handleOpen();
              }
            }
          : undefined
      }
      className={clsx(
        "group flex w-full items-center gap-4 rounded-[var(--radius-md)] border bg-[var(--color-surface)] px-4 py-3 text-left",
        topEntry ? "surface-interactive cursor-pointer" : "",
        isLoud ? "border-[var(--color-border-strong)]" : "border-[var(--color-border)]",
      )}
      style={isLoud ? { borderLeftWidth: 3, borderLeftColor: accent } : undefined}
    >
      <div className="w-16 shrink-0">
        <div className="font-mono text-sm font-semibold tracking-tight">{item.symbol}</div>
        <div className="truncate text-[var(--text-micro)] text-[var(--color-text-faint)]">{item.sector}</div>
      </div>

      <div className="w-20 shrink-0 text-right">
        <div className="tabular text-[var(--text-small)] font-medium">{snapshot ? `\u20B9${snapshot.price.toFixed(2)}` : "\u2014"}</div>
        <div
          className={clsx(
            "tabular text-[var(--text-micro)]",
            returnPct == null ? "text-[var(--color-text-faint)]" : returnPct >= 0 ? "text-[var(--color-positive)]" : "text-[var(--color-negative)]",
          )}
        >
          {returnPct == null ? "\u2014" : `${returnPct >= 0 ? "+" : ""}${returnPct.toFixed(2)}%`}
        </div>
      </div>

      {/* Mobile-only compact signal indicator: on narrow screens the
          sparkline and full signal-type row are hidden below, so this is a
          genuine reorganization (a small severity badge) rather than just
          losing the information. */}
      {topEntry && (
        <div
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[var(--text-micro)] font-semibold tabular sm:hidden"
          style={{ backgroundColor: `${accent}1A`, color: accent }}
        >
          {topEntry.currentSeverity}
        </div>
      )}

      <div className="hidden h-8 min-w-[72px] flex-1 sm:block">
        {sparkData.length >= 2 && (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={sparkData} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
              <YAxis hide domain={["dataMin", "dataMax"]} />
              <Line type="monotone" dataKey="v" stroke={sparkColor} strokeWidth={1.5} dot={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="hidden min-w-0 flex-1 md:block">
        {topEntry ? (
          <div className="flex items-center gap-2">
            <div className="relative h-1.5 w-16 shrink-0 overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
              <div
                className="absolute inset-y-0 left-0 rounded-full"
                style={{ width: `${Math.min(100, topEntry.currentSeverity)}%`, backgroundColor: accent }}
              />
            </div>
            <span className="truncate text-[var(--text-small)] text-[var(--color-text-muted)]">
              {signalTypeLabel(topEntry.signalType)}
            </span>
            <span className="hidden shrink-0 text-[var(--text-micro)] font-medium text-[var(--color-accent-strong)] opacity-0 transition-opacity group-hover:opacity-100 lg:inline">
              Why? →
            </span>
          </div>
        ) : (
          <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">Quiet</span>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {trust && <StatusPill label={trust.status} tone={trustTone(trust.status)} />}
        {topEntry ? (
          <StatusPill label={ledgerStatusLabel(topEntry.status)} tone={ledgerStatusTone(topEntry.status)} />
        ) : (
          <StatusPill label="Normal" tone="neutral" />
        )}
      </div>

      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          onRemove();
        }}
        aria-label={`Remove ${item.symbol} from watchlist`}
        className="shrink-0 text-[var(--text-micro)] text-[var(--color-text-faint)] transition-opacity hover:text-[var(--color-negative)] sm:opacity-0 sm:focus-visible:opacity-100 sm:group-hover:opacity-100"
      >
        Remove
      </button>
    </div>
  );
}
