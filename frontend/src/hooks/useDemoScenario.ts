import { useMutation, useQueryClient } from "@tanstack/react-query";
import { watchlistApi, marketApi, reconciliationApi } from "../services/endpoints";
import type { ReconciliationResult } from "../types";

// The exact symbols the backend's deterministic demo scenario is scripted
// around (HackathonDemoScenario) - not fabricated, just named so the demo
// watchlist has something to react to. All prices, returns, and severities
// still come entirely from the real signal engine running on real generated
// snapshots; nothing here is a hardcoded UI value.
const DEMO_SYMBOLS = ["BHRT", "GNGS", "HIML", "SAHY", "ARGY"];
const DEMO_WATCHLIST_NAME = "Demo Scenario";

async function ensureDemoWatchlist(): Promise<string> {
  const lists = await watchlistApi.list();
  const existing = lists.find((w) => w.name === DEMO_WATCHLIST_NAME);
  const watchlist = existing ?? (await watchlistApi.create(DEMO_WATCHLIST_NAME));

  const items = await watchlistApi.items(watchlist.id);
  const have = new Set(items.map((i) => i.symbol));
  for (const symbol of DEMO_SYMBOLS) {
    if (!have.has(symbol)) {
      await watchlistApi.addItem(watchlist.id, symbol);
    }
  }
  return watchlist.id;
}

function requestId(): string {
  return crypto.randomUUID();
}

export interface DemoScenarioOutcome {
  baseline: ReconciliationResult;
  afterAwayPeriod: ReconciliationResult;
}

async function runDemoScenario(): Promise<DemoScenarioOutcome> {
  await marketApi.demoReset();
  await ensureDemoWatchlist();

  // Baseline check-in at the rally day - establishes "what the user saw
  // before leaving" so the second check-in has something real to diff
  // against.
  const baseline = await reconciliationApi.checkin(requestId());

  await marketApi.demoFastForward();

  // The actual "since you last checked" moment - 12 days revealed at once,
  // computed by the real reconciliation service against real ledger state.
  const afterAwayPeriod = await reconciliationApi.checkin(requestId());

  return { baseline, afterAwayPeriod };
}

export function useRunDemoScenario() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: runDemoScenario,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["watchlists"] });
      qc.invalidateQueries({ queryKey: ["ledger"] });
      qc.invalidateQueries({ queryKey: ["attention-debt"] });
      qc.invalidateQueries({ queryKey: ["debt-history"] });
    },
  });
}

export { DEMO_SYMBOLS, DEMO_WATCHLIST_NAME };
