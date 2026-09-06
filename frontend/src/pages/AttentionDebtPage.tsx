import { useMemo } from "react";
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts";
import { Card } from "../components/Card";
import { DebtGauge } from "../components/DebtGauge";
import { LedgerEntryRow } from "../components/LedgerEntryRow";
import { LoadingState, ErrorState, EmptyState } from "../components/StateViews";
import { useAttentionDebt, useDebtHistory, useLedger } from "../hooks/useApi";
import { countByStatus } from "../utils/statusStyles";

export function AttentionDebtPage() {
  const { data: debt, isLoading, isError, refetch } = useAttentionDebt();
  const { data: history } = useDebtHistory();
  const { data: ledger } = useLedger();

  const breakdown = useMemo(() => (ledger ? countByStatus(ledger.map((e) => e.status)) : undefined), [ledger]);

  const chartData = (history ?? []).map((h) => ({
    time: new Date(h.computedAt).toLocaleDateString(undefined, { month: "short", day: "numeric" }),
    debt: h.normalizedDebt,
  }));

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="text-[var(--text-h1)] font-semibold tracking-tight">Attention Debt</h1>
        <p className="mt-1 text-[var(--text-body)] text-[var(--color-text-muted)]">
          How much unresolved market information is currently competing for your attention, and whether it's
          converging or piling up.
        </p>
      </div>

      <Card className="flow-surface border-[var(--color-border-strong)] overflow-hidden">
        {isLoading && <LoadingState label={"Computing attention debt\u2026"} />}
        {isError && <ErrorState message="Couldn't load attention debt." onRetry={() => refetch()} />}
        {debt && <DebtGauge debt={debt} history={history} breakdown={breakdown} />}
      </Card>

      <Card>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Trajectory · full history
        </div>
        <div className="mt-3 h-56">
          {chartData.length < 2 ? (
            <EmptyState>
              Not enough history yet — check in a few times (or simulate an absence) to see a trend line.
            </EmptyState>
          ) : (
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="time" tick={{ fontSize: 12, fill: "var(--color-text-faint)" }} axisLine={false} tickLine={false} />
                <YAxis domain={[0, 100]} tick={{ fontSize: 12, fill: "var(--color-text-faint)" }} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={{ borderRadius: 8, border: "1px solid var(--color-border)", fontSize: 12 }}
                  formatter={(value) => [Math.round(Number(value)), "Debt"]}
                />
                <Line type="monotone" dataKey="debt" stroke="var(--color-accent)" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </Card>

      <div>
        <h2 className="text-[var(--text-small)] font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
          Contributing signals
        </h2>
        <div className="mt-2 flex flex-col gap-2">
          {debt?.topPriorities.length === 0 && (
            <Card>
              <EmptyState>No unresolved signals are currently contributing to your debt score.</EmptyState>
            </Card>
          )}
          {debt?.topPriorities.map((entry, i) => (
            <LedgerEntryRow key={entry.id} entry={entry} index={i} />
          ))}
        </div>
      </div>

      <Card className="text-[var(--text-small)] text-[var(--color-text-faint)]">
        <div className="font-medium uppercase tracking-wide text-[var(--color-text-muted)]">How this is calculated</div>
        <p className="mt-2 leading-relaxed">
          Each unresolved signal contributes severity × confidence × a persistence multiplier (up to 1.5×)
          to a raw debt total. The raw total is normalized to 0–100 with a bounded exponential curve, so debt
          saturates smoothly toward OVERLOADED rather than growing without limit. Trajectory compares the current
          score to the last computed one: a 5-point swing in either direction counts as converging or diverging.
        </p>
      </Card>
    </div>
  );
}
