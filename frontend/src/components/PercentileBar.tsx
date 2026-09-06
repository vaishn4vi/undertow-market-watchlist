import { motion } from "framer-motion";

export function PercentileBar({ percentile }: { percentile: number }) {
  const clamped = Math.max(0, Math.min(100, percentile));

  return (
    <div className="flex flex-col gap-2">
      <div className="relative h-2 rounded-full bg-[var(--color-surface-sunken)]">
        {/* shaded "typical" zone, roughly the middle of the distribution */}
        <div className="absolute inset-y-0 left-[10%] right-[10%] rounded-full bg-[var(--color-border)]" />
        <motion.div
          className="absolute top-1/2 h-3.5 w-3.5 -translate-y-1/2 rounded-full border-2 border-[var(--color-surface)]"
          style={{ backgroundColor: "var(--color-accent)" }}
          initial={{ left: "0%" }}
          animate={{ left: `calc(${clamped}% - 7px)` }}
          transition={{ duration: 0.6, ease: "easeOut" }}
        />
      </div>
      <div className="flex justify-between text-[var(--text-micro)] text-[var(--color-text-faint)]">
        <span>Typical day</span>
        <span>Most extreme</span>
      </div>
    </div>
  );
}
