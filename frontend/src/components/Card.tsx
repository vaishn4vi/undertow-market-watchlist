import type { PropsWithChildren } from "react";
import { clsx } from "clsx";

export function Card({
  children,
  className,
}: PropsWithChildren<{ className?: string }>) {
  return (
    <div
      className={clsx(
        "rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] p-5",
        className,
      )}
    >
      {children}
    </div>
  );
}
