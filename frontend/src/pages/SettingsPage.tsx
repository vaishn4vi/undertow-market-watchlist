import { Card } from "../components/Card";
import { LoadingState, ErrorState } from "../components/StateViews";
import { usePreferences } from "../hooks/useApi";

export function SettingsPage() {
  const { data: prefs, isLoading, isError, refetch } = usePreferences();

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Settings</h1>

      <Card>
        <div className="text-sm font-medium">Attention thresholds</div>
        <p className="mt-1 text-sm text-[var(--color-text-muted)]">
          Adjusts automatically based on how often you dismiss vs. act on each signal type. No manual tuning needed —
          shown here for transparency.
        </p>
        {isLoading && <LoadingState />}
        {isError && <ErrorState message="Couldn't load preferences." onRetry={() => refetch()} />}
        {prefs && (
          <dl className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-xs text-[var(--color-text-faint)]">Base threshold</dt>
              <dd className="tabular font-medium">{prefs.persistenceThreshold}</dd>
            </div>
            <div>
              <dt className="text-xs text-[var(--color-text-faint)]">Decoupling delta</dt>
              <dd className="tabular font-medium">{formatDelta(prefs.decouplingThresholdDelta)}</dd>
            </div>
            <div>
              <dt className="text-xs text-[var(--color-text-faint)]">Silence delta</dt>
              <dd className="tabular font-medium">{formatDelta(prefs.silenceThresholdDelta)}</dd>
            </div>
            <div>
              <dt className="text-xs text-[var(--color-text-faint)]">Abnormality delta</dt>
              <dd className="tabular font-medium">{formatDelta(prefs.abnormalityThresholdDelta)}</dd>
            </div>
          </dl>
        )}
      </Card>

      {[
        { title: "Watchlists", description: "Create, rename, and reorder your watchlists.", link: "/watchlist" },
        { title: "Data status", description: "Market data trust and resilience, across every symbol you track.", link: "/data-status" },
        { title: "Notifications", description: "When you're notified about new or worsening signals.", link: null },
        { title: "Simulate absence", description: "Run the seeded demo scenario for a full walkthrough.", link: "/" },
      ].map((section) => (
        <Card key={section.title} className="flex items-center justify-between">
          <div>
            <div className="text-sm font-medium">{section.title}</div>
            <div className="text-sm text-[var(--color-text-muted)]">{section.description}</div>
          </div>
          {section.link ? (
            <a href={section.link} className="text-xs font-medium text-[var(--color-accent-strong)] underline">
              Go
            </a>
          ) : (
            <span className="text-xs text-[var(--color-text-faint)]">Coming soon</span>
          )}
        </Card>
      ))}
    </div>
  );
}

function formatDelta(delta: number): string {
  if (delta === 0) return "\u00B10";
  return delta > 0 ? `+${delta}` : `${delta}`;
}
