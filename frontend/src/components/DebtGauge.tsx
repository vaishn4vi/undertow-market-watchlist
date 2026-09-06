import { motion } from "framer-motion";
import { AreaChart, Area, ResponsiveContainer, YAxis } from "recharts";
import type { AttentionDebt, DebtHistoryPoint } from "../types";
import { debtBandColor, type DebtBreakdown } from "../utils/statusStyles";

const TRAJECTORY_ARROW: Record<AttentionDebt["trajectory"], string> = {
  CONVERGING: "\u2193",
  STABLE: "\u2192",
  DIVERGING: "\u2191",
};

const TRAJECTORY_LABEL: Record<AttentionDebt["trajectory"], string> = {
  CONVERGING: "Converging",
  STABLE: "Stable",
  DIVERGING: "Diverging",
};

const TRAJECTORY_SENTENCE: Record<AttentionDebt["trajectory"], string> = {
  DIVERGING: "New unresolved signals are arriving faster than they're clearing.",
  CONVERGING: "Unresolved signals are clearing faster than new ones are appearing.",
  STABLE: "Unresolved signals are holding roughly steady.",
};

// Radial gauge geometry - a semicircle arc, normalized to pathLength=100 so
// the fill percentage maps directly to a 0-100 strokeDasharray.
const ARC_PATH = "M 24 108 A 84 84 0 0 1 192 108";

export function DebtGauge({
  debt,
  compact = false,
  history,
  breakdown,
}: {
  debt: AttentionDebt;
  compact?: boolean;
  /** Recent debt-history points for the small trajectory sparkline. Optional - omitted on compact usages. */
  history?: DebtHistoryPoint[];
  /** Full-ledger status counts (not just topPriorities) for the signal-mix chips. Optional. */
  breakdown?: DebtBreakdown;
}) {
  const color = debtBandColor(debt.band);
  const filled = Math.max(0, Math.min(100, debt.normalizedDebt));
  const sparkData = (history ?? []).slice(-14).map((h, i) => ({ i, v: h.normalizedDebt }));

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:gap-6">
        <div className="relative mx-auto w-full max-w-[216px] shrink-0 sm:mx-0">
          <svg viewBox="0 0 216 120" className="w-full" role="img" aria-label={`Attention debt ${Math.round(filled)} out of 100, ${debt.band}`}>
            <path
              d={ARC_PATH}
              fill="none"
              stroke="var(--color-surface-sunken)"
              strokeWidth={14}
              strokeLinecap="round"
              pathLength={100}
            />
            <motion.path
              d={ARC_PATH}
              fill="none"
              stroke={color}
              strokeWidth={14}
              strokeLinecap="round"
              pathLength={100}
              initial={{ strokeDasharray: "0 100" }}
              animate={{ strokeDasharray: `${filled} ${100 - filled}` }}
              transition={{ duration: 0.8, ease: "easeOut" }}
            />
          </svg>
          <div className="absolute inset-x-0 bottom-1 flex flex-col items-center">
            <motion.span
              key={Math.round(filled)}
              initial={{ opacity: 0.3, y: 3 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.3 }}
              className="font-semibold tabular tracking-tight"
              style={{ color, fontSize: compact ? "var(--text-h1)" : "var(--text-display)" }}
            >
              {Math.round(filled)}
            </motion.span>
            <span className="text-[var(--text-micro)] text-[var(--color-text-faint)]">/ 100</span>
          </div>
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span
              className="rounded-[var(--radius-pill)] px-2.5 py-0.5 text-[var(--text-small)] font-semibold"
              style={{ backgroundColor: `${color}1A`, color }}
            >
              {debt.band}
            </span>
            <span style={{ color }} className="text-[var(--text-body)] font-medium">
              {TRAJECTORY_ARROW[debt.trajectory]} {TRAJECTORY_LABEL[debt.trajectory]}
            </span>

            {sparkData.length >= 2 && (
              <div className="h-8 w-20 shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={sparkData} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
                    <YAxis domain={[0, 100]} hide />
                    <defs>
                      <linearGradient id="debt-spark-fill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor={color} stopOpacity={0.35} />
                        <stop offset="100%" stopColor={color} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <Area type="monotone" dataKey="v" stroke={color} strokeWidth={1.5} fill="url(#debt-spark-fill)" isAnimationActive={false} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            )}
          </div>

          <p className="mt-2 text-[var(--text-small)] leading-relaxed text-[var(--color-text-muted)]">
            {debt.explanation || TRAJECTORY_SENTENCE[debt.trajectory]}
          </p>
        </div>
      </div>

      {breakdown && Object.values(breakdown).some((v) => v > 0) && (
        <>
          <div className="flex flex-wrap gap-x-4 gap-y-1.5 border-t border-[var(--color-border)] pt-3 text-[var(--text-small)] text-[var(--color-text-muted)]">
            {breakdown.new > 0 && <BreakdownChip label="new" value={breakdown.new} />}
            {breakdown.worsened > 0 && <BreakdownChip label="worsened" value={breakdown.worsened} color="var(--color-negative)" prefix={"\u2191"} />}
            {breakdown.active > 0 && <BreakdownChip label="active" value={breakdown.active} />}
            {breakdown.persisted > 0 && <BreakdownChip label="persisted" value={breakdown.persisted} />}
            {breakdown.unverified > 0 && <BreakdownChip label="unverified" value={breakdown.unverified} color="var(--color-text-faint)" prefix={"\u26A0"} />}
            {breakdown.resolved > 0 && <BreakdownChip label="resolved" value={breakdown.resolved} color="var(--color-positive)" />}
          </div>
          {!compact && <DebtFlowBar breakdown={breakdown} />}
        </>
      )}
    </div>
  );
}

// Inflow (new + worsened signals - adding to the backlog) vs outflow
// (resolved signals - draining it), drawn as a single diverging bar around
// a center line. This is the "attention debt accumulates / drains" idea
// made visible, using the same real counts as the chips above - not a
// separate metric.
function DebtFlowBar({ breakdown }: { breakdown: DebtBreakdown }) {
  const inflow = breakdown.new + breakdown.worsened;
  const outflow = breakdown.resolved;
  const total = Math.max(1, inflow + outflow);
  const inflowPct = (inflow / total) * 100;
  const outflowPct = (outflow / total) * 100;

  if (inflow === 0 && outflow === 0) return null;

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between text-[var(--text-micro)] text-[var(--color-text-faint)]">
        <span>{"\u2190"} Resolving</span>
        <span>Accumulating {"\u2192"}</span>
      </div>
      <div className="relative flex h-2 overflow-hidden rounded-full bg-[var(--color-surface-sunken)]">
        <div className="flex flex-1 justify-end">
          <motion.div
            className="h-full rounded-l-full"
            style={{ backgroundColor: "var(--color-positive)" }}
            initial={{ width: 0 }}
            animate={{ width: `${outflowPct}%` }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          />
        </div>
        <div className="w-px shrink-0 bg-[var(--color-border-strong)]" />
        <div className="flex flex-1">
          <motion.div
            className="h-full rounded-r-full"
            style={{ backgroundColor: "var(--color-negative)" }}
            initial={{ width: 0 }}
            animate={{ width: `${inflowPct}%` }}
            transition={{ duration: 0.6, ease: "easeOut" }}
          />
        </div>
      </div>
      <div className="flex items-center justify-between text-[var(--text-micro)] tabular text-[var(--color-text-muted)]">
        <span>{outflow} resolved</span>
        <span>{inflow} new or worsened</span>
      </div>
    </div>
  );
}

function BreakdownChip({ label, value, color, prefix }: { label: string; value: number; color?: string; prefix?: string }) {
  return (
    <span className="flex items-center gap-1" style={color ? { color } : undefined}>
      {prefix && <span>{prefix}</span>}
      <span className="tabular font-semibold">{value}</span>
      <span>{label}</span>
    </span>
  );
}

