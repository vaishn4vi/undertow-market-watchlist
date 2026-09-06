import { AnimatePresence, motion } from "framer-motion";
import { Link } from "react-router-dom";
import { X } from "lucide-react";
import { LoadingState, ErrorState } from "./StateViews";
import { StatusPill } from "./StatusPill";
import { SignalEvidencePanel } from "./SignalEvidencePanel";
import { useSignalEvidence } from "../hooks/useApi";
import { ledgerStatusLabel, ledgerStatusTone, signalTypeLabel, severityColor } from "../utils/statusStyles";
import type { EvidenceDrawerTarget } from "../contexts/evidenceDrawerContextObject";

export function EvidenceDrawer({ target, onClose }: { target: EvidenceDrawerTarget | null; onClose: () => void }) {
  const { data: evidence, isLoading, isError, refetch } = useSignalEvidence(target?.signalEventId);

  return (
    <AnimatePresence>
      {target && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="fixed inset-0 z-40 bg-black/25"
            onClick={onClose}
          />
          <motion.div
            role="dialog"
            aria-modal="true"
            aria-label={`Why am I seeing ${target.symbol}`}
            initial={{ x: "100%" }}
            animate={{ x: 0 }}
            exit={{ x: "100%" }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
            className="fixed inset-y-0 right-0 z-50 flex w-full max-w-full flex-col border-l border-[var(--color-border)] bg-[var(--color-surface)] shadow-[var(--shadow-lg)] sm:max-w-[440px]"
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--color-border)] px-5 py-4">
              <div>
                <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
                  Why am I seeing this?
                </div>
                <div className="mt-1 flex flex-wrap items-center gap-2">
                  <span className="font-mono text-[var(--text-h2)] font-semibold tracking-tight">{target.symbol}</span>
                  <span className="text-[var(--text-small)] text-[var(--color-text-muted)]">{signalTypeLabel(target.type)}</span>
                  <StatusPill label={ledgerStatusLabel(target.status)} tone={ledgerStatusTone(target.status)} />
                </div>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Close"
                className="shrink-0 rounded-[var(--radius-sm)] p-1.5 text-[var(--color-text-faint)] hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="flex items-center gap-4 border-b border-[var(--color-border)] px-5 py-4">
              <div className="flex items-baseline gap-1.5">
                <span className="tabular text-[var(--text-display)] font-semibold tracking-tight" style={{ color: severityColor(target.severity) }}>
                  {target.severity}
                </span>
                <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">/ 100 severity</span>
              </div>
              {target.persistenceCount != null && target.persistenceCount > 1 && (
                <>
                  <div className="h-8 w-px bg-[var(--color-border)]" />
                  <div className="text-[var(--text-small)] text-[var(--color-text-muted)]">
                    Seen <span className="tabular font-medium text-[var(--color-text)]">{target.persistenceCount}</span> times
                  </div>
                </>
              )}
            </div>

            <div className="flex-1 overflow-y-auto px-5 py-5">
              {isLoading && <LoadingState label="Loading evidence…" />}
              {isError && <ErrorState message="Couldn't load evidence for this signal." onRetry={() => refetch()} />}
              {evidence && (
                <SignalEvidencePanel symbol={target.symbol} type={target.type} evidence={evidence} persistenceCount={target.persistenceCount} />
              )}
            </div>

            <div className="border-t border-[var(--color-border)] px-5 py-3">
              <Link
                to={`/signals/${target.signalEventId}`}
                state={{ symbol: target.symbol, type: target.type, severity: target.severity, status: target.status }}
                onClick={onClose}
                className="text-[var(--text-small)] font-medium text-[var(--color-accent-strong)] underline underline-offset-2"
              >
                View full signal detail →
              </Link>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  );
}
