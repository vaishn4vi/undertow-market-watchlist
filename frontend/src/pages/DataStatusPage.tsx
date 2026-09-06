import { Card } from "../components/Card";
import { LoadingState, ErrorState, EmptyState } from "../components/StateViews";
import { useDataTrustOverview } from "../hooks/useApi";
import { trustStatusColor, trustStatusDescription } from "../utils/statusStyles";
import type { TrustStatus } from "../types";

const STATUS_ORDER: TrustStatus[] = ["LIVE", "DELAYED", "STALE", "CONFLICTING", "UNAVAILABLE"];

export function DataStatusPage() {
  const { data: overview, isLoading, isError, refetch } = useDataTrustOverview();

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
          Resilience
        </div>
        <h1 className="mt-1 text-[var(--text-h1)] font-semibold tracking-tight">Data status</h1>
        <p className="mt-1 text-[var(--text-body)] text-[var(--color-text-muted)]">
          How much you can currently trust the market data behind your signals, across every symbol you track.
        </p>
      </div>

      {isLoading && (
        <Card>
          <LoadingState label="Checking data health…" />
        </Card>
      )}
      {isError && (
        <Card>
          <ErrorState message="Couldn't load data status." onRetry={() => refetch()} />
        </Card>
      )}

      {overview && overview.totalSymbols === 0 && (
        <Card>
          <EmptyState>
            You're not tracking any symbols yet.
            <br />
            Add stocks to a watchlist to see their data trust here.
          </EmptyState>
        </Card>
      )}

      {overview && overview.totalSymbols > 0 && (
        <>
          <Card className="border-[var(--color-border-strong)]">
            <div className="flex items-center gap-3">
              <span
                className={overview.overallStatus === "LIVE" ? "h-2.5 w-2.5 animate-pulse rounded-full" : "h-2.5 w-2.5 rounded-full"}
                style={{ backgroundColor: trustStatusColor(overview.overallStatus) }}
              />
              <span className="text-[var(--text-h1)] font-semibold tracking-tight" style={{ color: trustStatusColor(overview.overallStatus) }}>
                Market data {overview.overallStatus.toLowerCase()}
              </span>
            </div>
            <p className="mt-2 text-[var(--text-small)] text-[var(--color-text-muted)]">
              {trustStatusDescription(overview.overallStatus)} Reflects the worst status among the{" "}
              {overview.totalSymbols} symbol{overview.totalSymbols === 1 ? "" : "s"} you currently track.
            </p>
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-[var(--text-small)] text-[var(--color-text-muted)]">
              <span>
                <span className="tabular font-semibold text-[var(--color-text)]">{overview.distribution["LIVE"] ?? 0}</span>/
                <span className="tabular">{overview.totalSymbols}</span> symbols verified live
              </span>
              <span className="text-[var(--text-micro)] text-[var(--color-text-faint)]">
                Updated {new Date(overview.asOf).toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" })}
              </span>
            </div>
          </Card>

          <Card>
            <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
              Trust distribution
            </div>

            <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
              {STATUS_ORDER.map((status) => {
                const count = overview.distribution[status] ?? 0;
                if (count === 0) return null;
                const pct = (count / overview.totalSymbols) * 100;
                return (
                  <div
                    key={status}
                    style={{ width: `${pct}%`, backgroundColor: trustStatusColor(status) }}
                    className="h-full first:rounded-l-full last:rounded-r-full"
                  />
                );
              })}
            </div>

            <div className="mt-4 flex flex-col gap-3">
              {STATUS_ORDER.map((status) => {
                const count = overview.distribution[status] ?? 0;
                if (count === 0) return null;
                return (
                  <div key={status} className="flex items-start gap-3">
                    <span className="mt-1 h-1.5 w-1.5 shrink-0 rounded-full" style={{ backgroundColor: trustStatusColor(status) }} />
                    <div className="min-w-0 flex-1">
                      <div className="flex items-baseline gap-2">
                        <span className="text-[var(--text-small)] font-semibold">{status}</span>
                        <span className="tabular text-[var(--text-small)] text-[var(--color-text-faint)]">
                          {count} symbol{count === 1 ? "" : "s"}
                        </span>
                      </div>
                      <p className="text-[var(--text-small)] text-[var(--color-text-muted)]">{trustStatusDescription(status)}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
