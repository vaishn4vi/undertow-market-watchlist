import { useState } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import { clsx } from "clsx";
import { Home, ListChecks, History, Gauge, MoreHorizontal, Settings, Activity, X, LogOut } from "lucide-react";
import { useAuth } from "../hooks/useAuth";

const TABS = [
  { to: "/", label: "Home", icon: Home, end: true },
  { to: "/watchlist", label: "Watchlist", icon: ListChecks },
  { to: "/since-last-checked", label: "Changes", icon: History },
  { to: "/attention-debt", label: "Debt", icon: Gauge },
];

const MORE_ITEMS = [
  { to: "/replay", label: "Replay", icon: Activity },
  { to: "/settings", label: "Settings", icon: Settings },
  { to: "/data-status", label: "Data Status", icon: MoreHorizontal },
];

export function MobileBottomNav() {
  const [moreOpen, setMoreOpen] = useState(false);
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    setMoreOpen(false);
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <>
      <AnimatePresence>
        {moreOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-40 bg-black/20 md:hidden"
            onClick={() => setMoreOpen(false)}
          >
            <motion.div
              initial={{ y: "100%" }}
              animate={{ y: 0 }}
              exit={{ y: "100%" }}
              transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
              onClick={(e) => e.stopPropagation()}
              className="absolute inset-x-0 bottom-0 rounded-t-[var(--radius-lg)] border-t border-[var(--color-border)] bg-[var(--color-surface)] p-4 pb-8 shadow-[var(--shadow-lg)]"
            >
              <div className="mb-3 flex items-center justify-between">
                <span className="text-[var(--text-small)] font-semibold uppercase tracking-wide text-[var(--color-text-faint)]">
                  More
                </span>
                <button type="button" onClick={() => setMoreOpen(false)} aria-label="Close">
                  <X className="h-5 w-5 text-[var(--color-text-faint)]" />
                </button>
              </div>
              <div className="flex flex-col gap-1">
                {MORE_ITEMS.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    onClick={() => setMoreOpen(false)}
                    className={({ isActive }) =>
                      clsx(
                        "flex items-center gap-3 rounded-[var(--radius-sm)] px-3 py-3 text-[var(--text-body)]",
                        isActive ? "bg-[var(--color-accent-soft)] text-[var(--color-accent-strong)] font-medium" : "text-[var(--color-text-muted)]",
                      )
                    }
                  >
                    <item.icon className="h-5 w-5" />
                    {item.label}
                  </NavLink>
                ))}
                <div className="my-1 border-t border-[var(--color-border)]" />
                {user && (
                  <div className="truncate px-3 py-1 text-[var(--text-micro)] text-[var(--color-text-faint)]">
                    {user.email}
                  </div>
                )}
                <button
                  type="button"
                  onClick={handleLogout}
                  className="flex items-center gap-3 rounded-[var(--radius-sm)] px-3 py-3 text-left text-[var(--text-body)] text-[var(--color-text-muted)]"
                >
                  <LogOut className="h-5 w-5" />
                  Log out
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <nav className="fixed inset-x-0 bottom-0 z-30 flex border-t border-[var(--color-border)] bg-[var(--color-surface)] pb-[env(safe-area-inset-bottom)] md:hidden">
        {TABS.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            end={tab.end}
            className={({ isActive }) =>
              clsx(
                "flex flex-1 flex-col items-center gap-0.5 py-2.5 text-[var(--text-micro)] font-medium",
                isActive ? "text-[var(--color-accent-strong)]" : "text-[var(--color-text-faint)]",
              )
            }
          >
            {({ isActive }) => (
              <>
                <tab.icon className="h-5 w-5" strokeWidth={isActive ? 2.25 : 1.75} />
                {tab.label}
              </>
            )}
          </NavLink>
        ))}
        <button
          type="button"
          onClick={() => setMoreOpen(true)}
          className="flex flex-1 flex-col items-center gap-0.5 py-2.5 text-[var(--text-micro)] font-medium text-[var(--color-text-faint)]"
        >
          <MoreHorizontal className="h-5 w-5" strokeWidth={1.75} />
          More
        </button>
      </nav>
    </>
  );
}
