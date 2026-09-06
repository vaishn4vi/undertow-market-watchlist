import { MovementComparisonChart } from "./MovementComparisonChart";
import { PercentileBar } from "./PercentileBar";
import { explainSignal } from "../utils/statusStyles";
import type { SignalEvidence, SignalType } from "../types";

// Deterministic, template-based explanation from already-computed evidence.
// This is the ONLY place anything resembling "explanation" happens on the
// frontend, and it does no inference of its own - every number here comes
// straight from the backend's structured evidence (an explanation layer may
// only restate evidence the engine already produced).

interface ChecklistItem {
  label: string;
  met: boolean;
  detail: string;
}

// Presentation-level checklist over real, already-computed evidence fields.
// The thresholds here (e.g. "percentile >= 70 reads as outside typical
// range") are just readability choices for how to *display* a number the
// backend already produced - they don't re-decide whether the signal fired.
// The signal existing at all means the deterministic engine already made
// that call.
function buildChecklist(type: SignalType | undefined, evidence: SignalEvidence, persistenceCount: number | undefined): ChecklistItem[] {
  const items: ChecklistItem[] = [];

  if (type === "DECOUPLING" || type === "SILENCE" || type === undefined) {
    items.push({
      label: "Diverged from sector-implied expectation",
      met: Math.abs(evidence.deviation) > 0,
      detail: `${evidence.deviation >= 0 ? "+" : ""}${evidence.deviation.toFixed(2)}pp deviation`,
    });
  }

  items.push({
    label: "Outside typical historical range",
    met: evidence.historicalPercentile >= 70,
    detail: `${evidence.historicalPercentile.toFixed(0)}th percentile of daily moves`,
  });

  if (persistenceCount != null) {
    items.push({
      label: "Persisted across multiple observations",
      met: persistenceCount > 1,
      detail: persistenceCount > 1 ? `Seen ${persistenceCount} times` : "First observation",
    });
  }

  return items;
}

export function SignalEvidencePanel({
  symbol,
  type,
  evidence,
  persistenceCount,
}: {
  symbol: string;
  type: SignalType | undefined;
  evidence: SignalEvidence;
  persistenceCount?: number;
}) {
  const checklist = buildChecklist(type, evidence, persistenceCount);

  return (
    <div className="flex flex-col gap-6">
      <p className="text-[var(--text-body)] leading-relaxed text-[var(--color-text)]">
        {explainSignal(type, symbol, evidence)}
      </p>

      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Why this triggered
        </div>
        <ul className="mt-2 flex flex-col gap-1.5">
          {checklist.map((item) => (
            <li key={item.label} className="flex items-start gap-2 text-[var(--text-small)]">
              <span
                className="mt-0.5 shrink-0 font-semibold"
                style={{ color: item.met ? "var(--color-positive)" : "var(--color-text-faint)" }}
                aria-hidden
              >
                {item.met ? "\u2713" : "\u2013"}
              </span>
              <span className={item.met ? "text-[var(--color-text)]" : "text-[var(--color-text-faint)]"}>
                {item.label}
                <span className="text-[var(--color-text-faint)]"> {"\u2014"} {item.detail}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Movement comparison
        </div>
        <div className="mt-3">
          <MovementComparisonChart
            stockReturn={evidence.stockReturn}
            sectorReturn={evidence.sectorReturn}
            expectedReturn={evidence.expectedReturn}
          />
        </div>
      </div>

      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Historical percentile
        </div>
        <p className="mt-1 text-[var(--text-small)] text-[var(--color-text-muted)]">
          Today's move is larger than {evidence.historicalPercentile.toFixed(0)}% of this stock's typical daily moves.
        </p>
        <div className="mt-3">
          <PercentileBar percentile={evidence.historicalPercentile} />
        </div>
      </div>

      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-wide text-[var(--color-text-faint)]">
          Raw evidence
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-3 text-[var(--text-small)] sm:grid-cols-3">
          <EvidenceStat label="Stock return" value={`${evidence.stockReturn.toFixed(2)}%`} />
          <EvidenceStat label="Sector return" value={`${evidence.sectorReturn.toFixed(2)}%`} />
          <EvidenceStat label="Expected return" value={`${evidence.expectedReturn.toFixed(2)}%`} />
          <EvidenceStat label="Deviation" value={`${evidence.deviation.toFixed(2)}pp`} />
          <EvidenceStat label="Historical percentile" value={`${evidence.historicalPercentile.toFixed(0)}%`} />
          {Object.entries(evidence.extra).map(([key, value]) => (
            <EvidenceStat key={key} label={key} value={typeof value === "number" ? value.toFixed(3) : String(value)} />
          ))}
        </dl>
      </div>
    </div>
  );
}

function EvidenceStat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[var(--text-micro)] text-[var(--color-text-faint)]">{label}</dt>
      <dd className="tabular font-medium text-[var(--color-text)]">{value}</dd>
    </div>
  );
}
