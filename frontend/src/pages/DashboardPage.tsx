import { useMemo, useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Card } from "../components/Card";
import { DebtGauge } from "../components/DebtGauge";
import { LedgerEntryRow } from "../components/LedgerEntryRow";
import { LoadingState, ErrorState, EmptyState } from "../components/StateViews";
import { useAttentionDebt, useDebtHistory, useLedger } from "../hooks/useApi";
import { countByStatus } from "../utils/statusStyles";
import { useRunDemoScenario } from "../hooks/useDemoScenario";
import type { DemoScenarioOutcome } from "../hooks/useDemoScenario";

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "Good morning.";
  if (hour < 18) return "Good afternoon.";
  return "Good evening.";
}

export function DashboardPage() {
  const { data: debt, isLoading, isError, refetch } = useAttentionDebt();
  const { data: history } = useDebtHistory();
  const { data: ledger } = useLedger();
  const demoScenario = useRunDemoScenario();
  const [lastOutcome, setLastOutcome] = useState<DemoScenarioOutcome | null>(null);

  const breakdown = useMemo(() => (ledger ? countByStatus(ledger.map((e) => e.status)) : undefined), [ledger]);

  const handleRunDemo = () => {
    demoScenario.mutate(undefined, {
      onSuccess: (outcome) => setLastOutcome(outcome),
    });
  };

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
            Undertow
          </div>
          <h1 className="mt-1 text-[var(--text-h1)] font-semibold tracking-tight">{greeting()}</h1>
          <p className="mt-1 text-[var(--text-body)] text-[var(--color-text-muted)]">
            Here's what changed while you were away.
          </p>
        </div>
        <button
          type="button"
          onClick={handleRunDemo}
          disabled={demoScenario.isPending}
          className="surface-interactive shrink-0 rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-2 text-[var(--text-small)] font-medium text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent-strong)] disabled:opacity-50"
        >
          {demoScenario.isPending ? "Simulating absence\u2026" : "Simulate absence"}
        </button>
      </div>

      <AnimatePresence>
        {lastOutcome && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
          >
            <Card className="border-[var(--color-accent)] bg-[var(--color-accent-soft)] shadow-[var(--shadow-sm)]">
              <div className="text-[var(--text-micro)] font-semibold uppercase tracking-wide text-[var(--color-accent-strong)]">
                You were away · {lastOutcome.afterAwayPeriod.daysAway.toFixed(1)} days
              </div>
              <p className="mt-2 text-[var(--text-body)] text-[var(--color-text)]">
                <span className="tabular font-semibold">{lastOutcome.afterAwayPeriod.totalMeaningfulChanges}</span>{" "}
                meaningful changes detected — {lastOutcome.afterAwayPeriod.resolvedCount} resolved,{" "}
                {lastOutcome.afterAwayPeriod.activeCount} active, {lastOutcome.afterAwayPeriod.worsenedCount} worsened,{" "}
                {lastOutcome.afterAwayPeriod.unverifiableCount} unverifiable, {lastOutcome.afterAwayPeriod.newCount} new.
              </p>
              <div className="mt-2 flex items-center gap-2 text-[var(--text-small)] text-[var(--color-text-muted)]">
                <span>Attention Debt</span>
                <span className="tabular font-semibold text-[var(--color-text)]">
                  {lastOutcome.baseline.debtBefore.toFixed(0)}
                </span>
                <span>→</span>
                <span className="tabular font-semibold text-[var(--color-accent-strong)]">
                  {lastOutcome.afterAwayPeriod.debtAfter.toFixed(0)}
                </span>
              </div>
              <a
                href="/since-last-checked"
                className="mt-2 inline-block text-[var(--text-small)] font-medium text-[var(--color-accent-strong)] underline underline-offset-2"
              >
                See the full reconciliation →
              </a>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>

      <Card className="flow-surface border-[var(--color-border-strong)] overflow-hidden">
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Attention Debt
        </div>
        <div className="mt-3">
          {isLoading && <LoadingState label={"Computing attention debt\u2026"} />}
          {isError && <ErrorState message="Couldn't load attention debt." onRetry={() => refetch()} />}
          {debt && <DebtGauge debt={debt} history={history} breakdown={breakdown} />}
        </div>
      </Card>

      <div>
        <div className="flex items-center justify-between">
          <h2 className="text-[var(--text-small)] font-medium uppercase tracking-wide text-[var(--color-text-muted)]">
            Top priorities
          </h2>
          {debt && debt.deferredCount > 0 && (
            <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">{debt.deferredCount} deferred</span>
          )}
        </div>
        {debt && debt.deferredCount > 0 && (debt.band === "HIGH" || debt.band === "OVERLOADED") && (
          <p className="mt-1 text-[var(--text-small)] text-[var(--color-text-faint)]">
            Attention Debt is {debt.band === "OVERLOADED" ? "overloaded" : "elevated"} — the queue has been
            automatically capped to what matters most.
          </p>
        )}
        <div className="mt-2 flex flex-col gap-2">
          {isLoading && <Card><LoadingState /></Card>}
          {debt && debt.topPriorities.length === 0 && (
            <Card>
              <EmptyState>
                No signals need your attention right now.
                <br />
                Add stocks to a watchlist or simulate an absence to see the system in action.
              </EmptyState>
            </Card>
          )}
          {debt?.topPriorities.map((entry, i) => (
            <LedgerEntryRow key={entry.id} entry={entry} index={i} />
          ))}
        </div>
      </div>
    </div>
  );
}
