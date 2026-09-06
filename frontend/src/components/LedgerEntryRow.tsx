import { motion } from "framer-motion";
import type { LedgerEntry } from "../types";
import { StatusPill } from "./StatusPill";
import { useTrust } from "../hooks/useApi";
import { useEvidenceDrawer } from "../hooks/useEvidenceDrawer";
import {
  ledgerStatusLabel,
  ledgerStatusTone,
  signalTypeLabel,
  signalTypeHint,
  severityColor,
  trustTone,
} from "../utils/statusStyles";

export function LedgerEntryRow({ entry, index = 0 }: { entry: LedgerEntry; index?: number }) {
  const { data: trust } = useTrust(entry.symbol);
  const { open } = useEvidenceDrawer();
  const color = severityColor(entry.currentSeverity);
  const severityChanged = entry.previousSeverity != null && entry.previousSeverity !== entry.currentSeverity;

  const handleOpen = () => {
    const signalEventId = entry.latestSignalEventId ?? entry.id;
    open({
      signalEventId,
      symbol: entry.symbol,
      type: entry.signalType,
      severity: entry.currentSeverity,
      status: entry.status,
      persistenceCount: entry.persistenceCount,
    });
  };

  return (
    <motion.button
      type="button"
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25, delay: index * 0.04 }}
      onClick={handleOpen}
      className="surface-interactive flex w-full flex-col gap-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3.5 text-left"
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="font-mono text-sm font-semibold tracking-tight">{entry.symbol}</span>
          <span className="truncate text-[var(--text-small)] text-[var(--color-text-muted)]">
            {signalTypeLabel(entry.signalType)}
          </span>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {entry.persistenceCount > 1 && (
            <span className="text-[var(--text-micro)] uppercase tracking-wide text-[var(--color-text-faint)]">
              {entry.persistenceCount}{"\u00D7"} seen
            </span>
          )}
          <StatusPill label={ledgerStatusLabel(entry.status)} tone={ledgerStatusTone(entry.status)} />
        </div>
      </div>

      <p className="text-[var(--text-small)] text-[var(--color-text-muted)]">{signalTypeHint(entry.signalType)}</p>

      <div className="flex items-center gap-4">
        <div className="flex flex-1 items-center gap-2">
          <div className="relative h-1.5 flex-1 overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
            <motion.div
              className="absolute inset-y-0 left-0 rounded-full"
              style={{ backgroundColor: color }}
              initial={{ width: 0 }}
              animate={{ width: `${Math.min(100, entry.currentSeverity)}%` }}
              transition={{ duration: 0.6, ease: "easeOut" }}
            />
          </div>
          <span className="tabular text-[var(--text-small)] font-semibold" style={{ color }}>
            {entry.currentSeverity}
          </span>
          {severityChanged && (
            <span
              className="text-[var(--text-micro)]"
              style={{ color: entry.currentSeverity > (entry.previousSeverity ?? 0) ? "var(--color-negative)" : "var(--color-positive)" }}
            >
              {entry.currentSeverity > (entry.previousSeverity ?? 0) ? "\u2191" : "\u2193"} was {entry.previousSeverity}
            </span>
          )}
        </div>
        {trust && <StatusPill label={trust.status} tone={trustTone(trust.status)} />}
      </div>

      <div className="text-[var(--text-micro)] font-medium text-[var(--color-accent-strong)]">
        Why am I seeing this? →
      </div>
    </motion.button>
  );
}
