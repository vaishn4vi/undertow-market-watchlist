import { NavLink } from "react-router-dom";
import { useDataTrustOverview } from "../hooks/useApi";
import { trustStatusColor } from "../utils/statusStyles";

function timeAgo(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  return `${hours}h ago`;
}

export function MarketDataPill() {
  const { data: overview } = useDataTrustOverview();

  if (!overview) {
    return (
      <div className="flex items-center gap-2 rounded-[var(--radius-pill)] border border-[var(--color-border)] px-3 py-1.5 text-[var(--text-small)] text-[var(--color-text-muted)]">
        <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-text-faint)]" />
        Market data —
      </div>
    );
  }

  const color = trustStatusColor(overview.overallStatus);
  const label = overview.overallStatus === "UNKNOWN" ? "No symbols tracked" : `Market data ${overview.overallStatus.toLowerCase()}`;
  const verifiedCount = overview.distribution["LIVE"] ?? 0;
  const tooltip =
    overview.overallStatus === "UNKNOWN"
      ? "Add stocks to a watchlist to see data trust here."
      : `${verifiedCount}/${overview.totalSymbols} symbols verified live \u00B7 updated ${timeAgo(overview.asOf)}`;

  return (
    <NavLink
      to="/data-status"
      title={tooltip}
      className="flex items-center gap-2 rounded-[var(--radius-pill)] border border-[var(--color-border)] px-3 py-1.5 text-[var(--text-small)] font-medium hover:border-[var(--color-accent)]"
    >
      <span
        className={overview.overallStatus === "LIVE" ? "h-1.5 w-1.5 animate-pulse rounded-full" : "h-1.5 w-1.5 rounded-full"}
        style={{ backgroundColor: color }}
      />
      <span className="hidden sm:inline" style={{ color }}>
        {label}
      </span>
      {overview.overallStatus !== "UNKNOWN" && (
        <span className="hidden text-[var(--color-text-faint)] lg:inline">
          {verifiedCount}/{overview.totalSymbols} verified
        </span>
      )}
    </NavLink>
  );
}
