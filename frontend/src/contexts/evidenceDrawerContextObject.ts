import { createContext } from "react";
import type { LedgerStatus, SignalType } from "../types";

export interface EvidenceDrawerTarget {
  signalEventId: string;
  symbol: string;
  type: SignalType;
  severity: number;
  status: LedgerStatus;
  persistenceCount?: number;
}

export interface EvidenceDrawerContextValue {
  open: (target: EvidenceDrawerTarget) => void;
  close: () => void;
}

export const EvidenceDrawerContext = createContext<EvidenceDrawerContextValue | null>(null);
