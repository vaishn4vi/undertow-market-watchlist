import { useParams, useLocation } from "react-router-dom";
import { Card } from "../components/Card";
import { StatusPill } from "../components/StatusPill";
import { LoadingState, ErrorState, EmptyState } from "../components/StateViews";
import { SignalEvidencePanel } from "../components/SignalEvidencePanel";
import { useSignalEvidence } from "../hooks/useApi";
import { ledgerStatusLabel, ledgerStatusTone, signalTypeLabel, severityColor } from "../utils/statusStyles";
import type { LedgerStatus, SignalType } from "../types";

interface NavState {
  symbol?: string;
  type?: SignalType;
  severity?: number;
  status?: LedgerStatus;
  persistenceCount?: number;
}

export function SignalDetailPage() {
  const { signalId } = useParams();
  const location = useLocation();
  const navState = (location.state as NavState) ?? {};
  const { data: evidence, isLoading, isError, refetch } = useSignalEvidence(signalId);

  const severityColorValue = navState.severity != null ? severityColor(navState.severity) : undefined;

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <div className="text-[var(--text-micro)] font-medium uppercase tracking-widest text-[var(--color-text-faint)]">
          Signal investigation
        </div>
        <div className="mt-1 flex flex-wrap items-center gap-2">
          <h1 className="font-mono text-[var(--text-h1)] font-semibold tracking-tight">{navState.symbol ?? "Signal"}</h1>
          {navState.type && (
            <span className="text-[var(--text-body)] text-[var(--color-text-muted)]">{signalTypeLabel(navState.type)}</span>
          )}
          {navState.status && <StatusPill label={ledgerStatusLabel(navState.status)} tone={ledgerStatusTone(navState.status)} />}
        </div>
      </div>

      {navState.severity != null && (
        <Card className="flex items-center gap-4">
          <div className="flex items-baseline gap-1.5">
            <span className="tabular text-[var(--text-display)] font-semibold tracking-tight" style={{ color: severityColorValue }}>
              {navState.severity}
            </span>
            <span className="text-[var(--text-small)] text-[var(--color-text-faint)]">/ 100 severity</span>
          </div>
          <div className="h-8 w-px bg-[var(--color-border)]" />
          <div className="min-w-0 flex-1 text-[var(--text-small)] text-[var(--color-text-muted)]">
            {navState.type ? signalTypeLabel(navState.type) : "Signal"} detected for {navState.symbol}.
          </div>
        </Card>
      )}

      {isLoading && (
        <Card>
          <LoadingState label="Loading evidence…" />
        </Card>
      )}
      {isError && (
        <Card>
          <ErrorState message="Couldn't load evidence for this signal." onRetry={() => refetch()} />
        </Card>
      )}
      {!signalId && !isLoading && (
        <Card>
          <EmptyState>No signal selected.</EmptyState>
        </Card>
      )}

      {evidence && (
        <Card>
          <SignalEvidencePanel
            symbol={navState.symbol ?? "This stock"}
            type={navState.type}
            evidence={evidence}
            persistenceCount={navState.persistenceCount}
          />
        </Card>
      )}
    </div>
  );
}
