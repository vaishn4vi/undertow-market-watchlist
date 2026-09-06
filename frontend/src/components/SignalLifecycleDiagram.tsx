import { motion } from "framer-motion";

interface LifecycleStage {
  label: string;
  value: number;
}

export function SignalLifecycleDiagram({
  detected,
  persisted,
  resolved,
  premature,
}: {
  detected: number;
  persisted: number;
  resolved: number;
  premature: number;
}) {
  // DETECTED and ACTIVE are the same underlying count in this data model -
  // a signal starts "active" the moment it's first detected. PERSISTED
  // only happens if it survives long enough to clear the hysteresis
  // threshold; RESOLVED is the terminal state either way. Signals that
  // resolve WITHOUT ever persisting are the ones discarded as transient
  // noise (premature) - shown as a branch off ACTIVE, not a fifth stage.
  const stages: LifecycleStage[] = [
    { label: "Detected", value: detected },
    { label: "Active", value: detected },
    { label: "Persisted", value: persisted },
    { label: "Resolved", value: resolved },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-stretch gap-1 sm:gap-2">
        {stages.map((stage, i) => (
          <div key={stage.label} className="flex flex-1 items-center gap-1 sm:gap-2">
            <motion.div
              initial={{ opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3, delay: i * 0.08 }}
              className="flex flex-1 flex-col items-center gap-1 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-4 text-center"
            >
              <span className="tabular text-[var(--text-h2)] font-semibold tracking-tight">{stage.value}</span>
              <span className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
                {stage.label}
              </span>
            </motion.div>
            {i < stages.length - 1 && (
              <span className="shrink-0 text-[var(--color-text-faint)]" aria-hidden>
                {"\u2192"}
              </span>
            )}
          </div>
        ))}
      </div>

      {detected > 0 && (
        <div className="flex items-start gap-2 rounded-[var(--radius-sm)] border border-dashed border-[var(--color-border-strong)] px-3 py-2.5 text-[var(--text-small)] text-[var(--color-text-muted)]">
          <span className="mt-0.5 shrink-0" aria-hidden>
            {"\u21B3"}
          </span>
          <span>
            <span className="tabular font-semibold text-[var(--color-text)]">{premature}</span> of {detected} detected
            signals resolved without ever persisting {"\u2014"} discarded as transient noise rather than treated as
            meaningful.
          </span>
        </div>
      )}
    </div>
  );
}
