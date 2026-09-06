import { useState } from "react";
import { Card } from "../components/Card";
import { EmptyState } from "../components/StateViews";
import { SignalLifecycleDiagram } from "../components/SignalLifecycleDiagram";
import { useBacktest } from "../hooks/useApi";

const RANGES: Array<{ label: string; days: 7 | 30 | 90 }> = [
  { label: "7D", days: 7 },
  { label: "30D", days: 30 },
  { label: "90D", days: 90 },
];

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
}

export function ReplayPage() {
  const [selected, setSelected] = useState<7 | 30 | 90>(30);
  const backtest = useBacktest();

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
          Validation
        </div>
        <h1 className="mt-1 text-[var(--text-h1)] font-semibold tracking-tight">Historical replay</h1>
        <p className="mt-1 text-[var(--text-body)] text-[var(--color-text-muted)]">
          Runs the same detection and persist/resolve logic against historical data that was never engineered to
          contain a signal — a way to see the false-positive rate honestly, on data with no signal to find. Not
          investment advice or prediction.
        </p>
      </div>

      <div className="flex flex-wrap gap-2">
        {RANGES.map((r) => (
          <button
            key={r.days}
            type="button"
            onClick={() => setSelected(r.days)}
            disabled={backtest.isPending}
            className={
              "rounded-[var(--radius-sm)] border px-3 py-1.5 text-[var(--text-small)] font-medium transition-colors disabled:opacity-50 " +
              (selected === r.days
                ? "border-[var(--color-accent)] bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)]"
                : "border-[var(--color-border-strong)] text-[var(--color-text-muted)] hover:border-[var(--color-accent)]")
            }
          >
            {r.label}
          </button>
        ))}
        <button
          type="button"
          onClick={() => backtest.mutate(selected)}
          disabled={backtest.isPending}
          className="surface-interactive ml-auto rounded-[var(--radius-sm)] bg-[var(--color-accent)] px-3 py-1.5 text-[var(--text-small)] font-medium text-white hover:bg-[var(--color-accent-strong)] disabled:opacity-50"
        >
          {backtest.isPending ? "Replaying…" : "Run replay"}
        </button>
      </div>

      {backtest.isPending && (
        <Card>
          <div className="text-[var(--text-small)] text-[var(--color-text-muted)]">
            Replaying {selected} days of historical market behavior against the deterministic signal engine…
          </div>
          <div className="relative mt-3 h-1.5 overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
            <div className="progress-indeterminate absolute inset-y-0 w-1/3 rounded-full bg-[var(--color-accent)]" />
          </div>
        </Card>
      )}

      {!backtest.data && !backtest.isPending && (
        <Card className="flex items-center justify-center py-14 text-[var(--text-small)] text-[var(--color-text-faint)]">
          <EmptyState>Run a replay to see signals detected, resolved, and the premature-alert rate.</EmptyState>
        </Card>
      )}

      {backtest.data && (
        <>
          <Card>
            <div className="flex items-center justify-between text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
              <span>Replay window</span>
              <span className="tabular normal-case text-[var(--color-text-muted)]">{backtest.data.rangeDays} days</span>
            </div>
            <div className="mt-3 flex items-center gap-3">
              <span className="text-[var(--text-small)] font-medium text-[var(--color-text)]">{formatDate(backtest.data.from)}</span>
              <div className="relative h-px flex-1 bg-[var(--color-border-strong)]">
                <div className="absolute inset-y-0 left-0 right-0 flex items-center justify-center">
                  <span className="bg-[var(--color-surface)] px-2 text-[var(--text-micro)] text-[var(--color-text-faint)]">replayed</span>
                </div>
              </div>
              <span className="text-[var(--text-small)] font-medium text-[var(--color-text)]">{formatDate(backtest.data.to)}</span>
            </div>
          </Card>

          <Card>
            <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
              Signal lifecycle
            </div>
            <div className="mt-4">
              <SignalLifecycleDiagram
                detected={backtest.data.totalDetected}
                persisted={backtest.data.totalPersisted}
                resolved={backtest.data.totalResolved}
                premature={backtest.data.totalPremature}
              />
            </div>
          </Card>

          <Card className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Metric label="Detected" value={backtest.data.totalDetected} />
            <Metric label="Persisted" value={backtest.data.totalPersisted} />
            <Metric label="Resolved" value={backtest.data.totalResolved} />
            <Metric label="Premature" value={backtest.data.totalPremature} />
          </Card>
          <Card className="flex flex-wrap gap-6">
            <div>
              <div className="text-[var(--text-micro)] text-[var(--color-text-faint)]">Mean-reversion rate</div>
              <div className="tabular text-[var(--text-h2)] font-semibold">{(backtest.data.meanReversionRate * 100).toFixed(1)}%</div>
            </div>
            <div>
              <div className="text-[var(--text-micro)] text-[var(--color-text-faint)]">Avg. signal lifetime</div>
              <div className="tabular text-[var(--text-h2)] font-semibold">{backtest.data.averageSignalLifetimeDays.toFixed(1)} days</div>
            </div>
          </Card>

          <Card className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full text-[var(--text-small)]">
                <thead>
                  <tr className="border-b border-[var(--color-border)] text-left text-[var(--text-micro)] text-[var(--color-text-faint)]">
                    <th className="px-4 py-3 font-medium">Symbol</th>
                    <th className="px-4 py-3 font-medium">Detected</th>
                    <th className="px-4 py-3 font-medium">Persisted</th>
                    <th className="px-4 py-3 font-medium">Resolved</th>
                    <th className="px-4 py-3 font-medium">Premature</th>
                  </tr>
                </thead>
                <tbody>
                  {backtest.data.bySymbol
                    .filter((s) => s.detected > 0)
                    .map((s) => (
                      <tr key={s.symbol} className="border-b border-[var(--color-border)] last:border-0">
                        <td className="px-4 py-2 font-mono font-medium">{s.symbol}</td>
                        <td className="px-4 py-2 tabular">{s.detected}</td>
                        <td className="px-4 py-2 tabular">{s.persisted}</td>
                        <td className="px-4 py-2 tabular">{s.resolved}</td>
                        <td className="px-4 py-2 tabular">{s.prematureAlerts}</td>
                      </tr>
                    ))}
                  {backtest.data.bySymbol.every((s) => s.detected === 0) && (
                    <tr>
                      <td colSpan={5} className="px-4 py-8 text-center text-[var(--color-text-faint)]">
                        No signals detected in this window on pure baseline data — a healthy sign for the false-positive rate.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </Card>
        </>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div>
      <div className="text-[var(--text-micro)] text-[var(--color-text-faint)]">{label}</div>
      <div className="tabular text-[var(--text-h1)] font-semibold tracking-tight">{value}</div>
    </div>
  );
}
