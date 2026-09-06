import { clsx } from "clsx";

type Tone = "neutral" | "positive" | "negative" | "warning" | "accent";

const TONE_CLASSES: Record<Tone, string> = {
  neutral: "bg-[var(--color-surface-sunken)] text-[var(--color-text-muted)]",
  positive: "bg-[var(--color-positive-soft)] text-[var(--color-positive)]",
  negative: "bg-[var(--color-negative-soft)] text-[var(--color-negative)]",
  warning: "bg-[#FBF3E4] text-[var(--color-debt-moderate)]",
  accent: "bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)]",
};

export function StatusPill({
  label,
  tone = "neutral",
}: {
  label: string;
  tone?: Tone;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-[var(--radius-pill)] px-2.5 py-0.5 text-xs font-medium",
        TONE_CLASSES[tone],
      )}
    >
      {label}
    </span>
  );
}
