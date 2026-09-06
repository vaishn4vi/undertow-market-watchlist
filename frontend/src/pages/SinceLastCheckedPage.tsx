import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import { Card } from "../components/Card";
import { StatusPill } from "../components/StatusPill";
import { LoadingState, EmptyState } from "../components/StateViews";
import { useCheckin, useLedger } from "../hooks/useApi";
import { useEvidenceDrawer } from "../hooks/useEvidenceDrawer";
import { outcomeTone, outcomeSymbol, signalTypeLabel, severityColor } from "../utils/statusStyles";
import type { LedgerEntry, ReconciliationEvent, ReconciliationOutcome } from "../types";

function requestId() {
  return crypto.randomUUID();
}

// Presentation order: most urgent first. Worsened and still-active demand
// attention now; newly detected is context; resolved is good news; and
// unverifiable is a caveat about data quality, not a signal outcome.
const OUTCOME_SECTIONS: Array<{ outcome: ReconciliationOutcome; title: string; blurb: string }> = [
  { outcome: "WORSENED", title: "Worsened", blurb: "Got more severe while you were away." },
  { outcome: "STILL_ACTIVE", title: "Still active", blurb: "Unresolved and still being tracked." },
  { outcome: "NEWLY_DETECTED", title: "New", blurb: "Detected for the first time." },
  { outcome: "RESOLVED", title: "Resolved", blurb: "No longer requires your attention." },
  { outcome: "UNVERIFIABLE", title: "Unverifiable", blurb: "Market data wasn't trustworthy enough to re-evaluate." },
];

export function SinceLastCheckedPage() {
  const checkin = useCheckin();
  const { data: ledger } = useLedger();
  const [hasChecked, setHasChecked] = useState(false);

  const handleCheckin = () => {
    checkin.mutate(requestId(), { onSuccess: () => setHasChecked(true) });
  };

  const result = checkin.data;

  // Reconciliation events don't carry a signal id of their own, but each
  // one corresponds to exactly one (symbol, signalType) ledger entry, which
  // does. Joining client-side against already-fetched ledger data - not
  // fabricating anything - is what lets "Why am I seeing this?" work here
  // without a backend change.
  const ledgerByKey = useMemo(() => {
    const map = new Map<string, LedgerEntry>();
    ledger?.forEach((e) => map.set(`${e.symbol}:${e.signalType}`, e));
    return map;
  }, [ledger]);

  const grouped = useMemo(() => {
    if (!result) return [];
    return OUTCOME_SECTIONS.map((section) => ({
      ...section,
      events: result.events.filter((e) => e.outcome === section.outcome),
    })).filter((section) => section.events.length > 0);
  }, [result]);

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
            Reconciliation
          </div>
          <h1 className="mt-1 text-[var(--text-h1)] font-semibold tracking-tight">Since you last checked</h1>
          <p className="mt-1 text-[var(--text-body)] text-[var(--color-text-muted)]">
            Everything that happened while you were away, reconciled against the latest market state.
          </p>
        </div>
        <button
          type="button"
          onClick={handleCheckin}
          disabled={checkin.isPending}
          className="surface-interactive shrink-0 rounded-[var(--radius-sm)] bg-[var(--color-accent)] px-3 py-2 text-[var(--text-small)] font-medium text-white hover:bg-[var(--color-accent-strong)] disabled:opacity-50"
        >
          {checkin.isPending ? "Reconciling…" : "Check in now"}
        </button>
      </div>

      {checkin.isPending && (
        <Card>
          <LoadingState label="Reconstructing what happened while you were away…" />
        </Card>
      )}

      {!hasChecked && !checkin.isPending && (
        <Card className="flex flex-col items-center gap-2 py-14 text-center">
          <EmptyState>
            Click "Check in now" to reconcile your watchlist against the latest market state.
          </EmptyState>
        </Card>
      )}

      {result && (
        <>
          <Card className="border-[var(--color-border-strong)]">
            <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
              You were away
            </div>
            <div className="mt-1 text-[var(--text-h1)] font-semibold tabular tracking-tight">
              {result.daysAway.toFixed(1)} <span className="text-[var(--text-body)] font-normal text-[var(--color-text-muted)]">days</span>
            </div>

            <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-[var(--text-small)]">
              <Stat label="Resolved" value={result.resolvedCount} tone="positive" />
              <Stat label="Active" value={result.activeCount} tone="warning" />
              <Stat label="Worsened" value={result.worsenedCount} tone="negative" />
              <Stat label="Unverifiable" value={result.unverifiableCount} tone="neutral" />
              <Stat label="New" value={result.newCount} tone="accent" />
            </div>

            <div className="mt-4 flex items-center gap-2 border-t border-[var(--color-border)] pt-3 text-[var(--text-small)] text-[var(--color-text-muted)]">
              <span>Attention Debt</span>
              <span className="tabular font-semibold text-[var(--color-text)]">{result.debtBefore.toFixed(0)}</span>
              <span>→</span>
              <span className="tabular font-semibold text-[var(--color-text)]">{result.debtAfter.toFixed(0)}</span>
              <span className="text-[var(--color-text-faint)]">({result.trajectory.toLowerCase()})</span>
            </div>
          </Card>

          {grouped.length === 0 && (
            <Card>
              <EmptyState>No signal activity to report for this check-in.</EmptyState>
            </Card>
          )}

          {grouped.map((section, sectionIndex) => (
            <div key={section.outcome}>
              <div className="mb-2 flex items-baseline gap-2">
                <h2 className="text-[var(--text-small)] font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
                  {section.title}
                </h2>
                <span className="tabular text-[var(--text-small)] text-[var(--color-text-faint)]">{section.events.length}</span>
              </div>
              <div className="flex flex-col gap-2">
                {section.events.map((event, i) => (
                  <ReconciliationEventCard
                    key={`${event.symbol}-${event.signalType}-${i}`}
                    event={event}
                    index={sectionIndex * 5 + i}
                    ledgerEntry={ledgerByKey.get(`${event.symbol}:${event.signalType}`)}
                  />
                ))}
              </div>
            </div>
          ))}
        </>
      )}
    </div>
  );
}

function ReconciliationEventCard({
  event,
  index,
  ledgerEntry,
}: {
  event: ReconciliationEvent;
  index: number;
  ledgerEntry: LedgerEntry | undefined;
}) {
  const hasBeforeAfter = event.severityBefore != null && event.severityAfter != null;
  const { open } = useEvidenceDrawer();

  const handleOpen = () => {
    if (!ledgerEntry) return;
    open({
      signalEventId: ledgerEntry.latestSignalEventId ?? ledgerEntry.id,
      symbol: ledgerEntry.symbol,
      type: ledgerEntry.signalType,
      severity: ledgerEntry.currentSeverity,
      status: ledgerEntry.status,
      persistenceCount: ledgerEntry.persistenceCount,
    });
  };

  return (
    <motion.div
      initial={{ opacity: 0, x: -8 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.25, delay: Math.min(index, 10) * 0.04 }}
    >
      <Card className="flex items-start gap-3">
        <span className="mt-0.5 text-lg leading-none">{outcomeSymbol(event.outcome)}</span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-[var(--text-body)] font-semibold">{event.symbol}</span>
            <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">{signalTypeLabel(event.signalType)}</span>
            <StatusPill label={event.outcome.replace("_", " ")} tone={outcomeTone(event.outcome)} />
          </div>
          <p className="mt-1 text-[var(--text-small)] text-[var(--color-text-muted)]">{event.narrative}</p>

          {hasBeforeAfter && (
            <div className="mt-2 flex items-center gap-2 text-[var(--text-small)]">
              <span className="text-[var(--color-text-faint)]">Severity</span>
              <span className="tabular font-medium" style={{ color: severityColor(event.severityBefore!) }}>
                {event.severityBefore}
              </span>
              <span className="text-[var(--color-text-faint)]">→</span>
              <span className="tabular font-medium" style={{ color: severityColor(event.severityAfter!) }}>
                {event.severityAfter}
              </span>
            </div>
          )}

          {ledgerEntry && (
            <button
              type="button"
              onClick={handleOpen}
              className="mt-2 text-[var(--text-micro)] font-medium text-[var(--color-accent-strong)] underline underline-offset-2"
            >
              Why am I seeing this?
            </button>
          )}
        </div>
      </Card>
    </motion.div>
  );
}

function Stat({ label, value, tone }: { label: string; value: number; tone: "positive" | "warning" | "negative" | "neutral" | "accent" }) {
  const colorMap: Record<string, string> = {
    positive: "var(--color-positive)",
    warning: "var(--color-debt-moderate)",
    negative: "var(--color-negative)",
    neutral: "var(--color-text-muted)",
    accent: "var(--color-accent-strong)",
  };
  return (
    <div className="flex items-center gap-1.5">
      <span className="tabular text-[var(--text-h2)] font-semibold" style={{ color: colorMap[tone] }}>
        {value}
      </span>
      <span className="text-[var(--text-micro)] text-[var(--color-text-faint)]">{label}</span>
    </div>
  );
}
