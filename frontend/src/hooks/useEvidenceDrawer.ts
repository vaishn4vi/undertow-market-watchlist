import { useContext } from "react";
import { EvidenceDrawerContext } from "../contexts/evidenceDrawerContextObject";

export function useEvidenceDrawer() {
  const ctx = useContext(EvidenceDrawerContext);
  if (!ctx) throw new Error("useEvidenceDrawer must be used within EvidenceDrawerProvider");
  return ctx;
}
