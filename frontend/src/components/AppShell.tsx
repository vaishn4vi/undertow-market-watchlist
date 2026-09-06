import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { clsx } from "clsx";
import { useAttentionDebt } from "../hooks/useApi";
import { useRunDemoScenario } from "../hooks/useDemoScenario";
import { useAuth } from "../hooks/useAuth";
import { debtBandColor } from "../utils/statusStyles";
import { MarketDataPill } from "./MarketDataPill";
import { MobileBottomNav } from "./MobileBottomNav";

const MAIN_NAV_ITEMS = [
  { to: "/", label: "Home", end: true },
  { to: "/watchlist", label: "Watchlist" },
  { to: "/since-last-checked", label: "Since Last Checked" },
  { to: "/attention-debt", label: "Attention Debt" },
  { to: "/replay", label: "Replay" },
];

const SECONDARY_NAV_ITEMS = [
  { to: "/settings", label: "Settings" },
  { to: "/data-status", label: "Data Status" },
];

export function AppShell() {
  const navigate = useNavigate();
  const demoScenario = useRunDemoScenario();
  const { user, logout } = useAuth();

  const handleRunDemo = () => {
    demoScenario.mutate(undefined, { onSuccess: () => navigate("/") });
  };

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="flex min-h-screen bg-[var(--color-bg)] text-[var(--color-text)]">
      <aside className="hidden w-56 shrink-0 flex-col border-r border-[var(--color-border)] px-4 py-6 md:flex">
        <div className="mb-8 px-2">
          <div className="text-[15px] font-semibold tracking-tight">Undertow</div>
          <div className="text-[var(--text-micro)] text-[var(--color-text-faint)]">Attention-aware watchlist</div>
        </div>

        <nav className="flex flex-col gap-0.5">
          {MAIN_NAV_ITEMS.map((item) => (
            <NavItem key={item.to} {...item} />
          ))}
        </nav>

        <div className="my-4 border-t border-[var(--color-border)]" />

        <nav className="flex flex-col gap-0.5">
          {SECONDARY_NAV_ITEMS.map((item) => (
            <NavItem key={item.to} {...item} />
          ))}
        </nav>

        <div className="mt-auto flex flex-col gap-2">
          {user && (
            <div className="flex items-center justify-between gap-2 rounded-[var(--radius-sm)] px-3 py-2 text-[var(--text-small)]">
              <span className="truncate text-[var(--color-text-muted)]" title={user.email}>
                {user.displayName}
              </span>
              <button
                type="button"
                onClick={handleLogout}
                className="shrink-0 text-[var(--text-micro)] font-medium text-[var(--color-text-faint)] hover:text-[var(--color-negative)]"
              >
                Log out
              </button>
            </div>
          )}
          <button
            type="button"
            onClick={handleRunDemo}
            disabled={demoScenario.isPending}
            className="surface-interactive w-full rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-2 text-left text-[var(--text-small)] font-medium text-[var(--color-text-muted)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent-strong)] disabled:opacity-50"
          >
            {demoScenario.isPending ? "Simulating absence…" : "Simulate absence"}
          </button>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between gap-2 border-b border-[var(--color-border)] px-4 py-3 sm:justify-end sm:px-8">
          <div className="text-[15px] font-semibold tracking-tight md:hidden">Undertow</div>
          <div className="flex items-center gap-2">
            <MarketDataPill />
            <DebtPill />
          </div>
        </header>

        <main className="flex-1 overflow-y-auto px-4 py-6 pb-24 sm:px-8 sm:py-8 md:pb-8">
          <Outlet />
        </main>
      </div>

      <MobileBottomNav />
    </div>
  );
}

function NavItem({ to, label, end }: { to: string; label: string; end?: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        clsx(
          "rounded-[var(--radius-sm)] px-3 py-2 text-[var(--text-small)] transition-colors",
          isActive
            ? "bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)] font-medium"
            : "text-[var(--color-text-muted)] hover:bg-[var(--color-surface-sunken)] hover:text-[var(--color-text)]",
        )
      }
    >
      {label}
    </NavLink>
  );
}

function DebtPill() {
  const { data: debt } = useAttentionDebt();

  if (!debt) {
    return (
      <div className="flex items-center gap-2 rounded-[var(--radius-pill)] border border-[var(--color-border)] px-3 py-1.5 text-[var(--text-small)] text-[var(--color-text-muted)]">
        <span className="h-1.5 w-1.5 rounded-full bg-[var(--color-text-faint)]" />
        Attention Debt —
      </div>
    );
  }

  const color = debtBandColor(debt.band);
  return (
    <NavLink
      to="/attention-debt"
      className="flex items-center gap-2 rounded-[var(--radius-pill)] border border-[var(--color-border)] px-3 py-1.5 text-[var(--text-small)] font-medium hover:border-[var(--color-accent)]"
    >
      <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }} />
      <span className="tabular" style={{ color }}>
        {Math.round(debt.normalizedDebt)}
      </span>
      <span className="hidden text-[var(--color-text-muted)] sm:inline">{debt.band}</span>
    </NavLink>
  );
}
