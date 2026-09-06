import { useState, type ReactNode } from "react";
import { EvidenceDrawer } from "../components/EvidenceDrawer";
import { EvidenceDrawerContext, type EvidenceDrawerTarget } from "./evidenceDrawerContextObject";

export function EvidenceDrawerProvider({ children }: { children: ReactNode }) {
  const [target, setTarget] = useState<EvidenceDrawerTarget | null>(null);

  return (
    <EvidenceDrawerContext.Provider value={{ open: setTarget, close: () => setTarget(null) }}>
      {children}
      <EvidenceDrawer target={target} onClose={() => setTarget(null)} />
    </EvidenceDrawerContext.Provider>
  );
}
