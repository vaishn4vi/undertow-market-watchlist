import type { PropsWithChildren } from "react";

export function LoadingState({ label = "Loading…" }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-14 text-sm text-[var(--color-text-faint)]">
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-[var(--color-accent)]" />
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-[var(--color-accent)] [animation-delay:150ms]" />
      <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-[var(--color-accent)] [animation-delay:300ms]" />
      <span className="ml-2">{label}</span>
    </div>
  );
}

export function ErrorState({ message = "Something went wrong.", onRetry }: { message?: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center gap-3 py-14 text-center">
      <span className="text-sm text-[var(--color-negative)]">{message}</span>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-1.5 text-xs font-medium text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent-strong)]"
        >
          Try again
        </button>
      )}
    </div>
  );
}

export function EmptyState({ children }: PropsWithChildren) {
  return (
    <div className="flex flex-col items-center gap-1 py-14 text-center text-sm text-[var(--color-text-faint)]">
      {children}
    </div>
  );
}
