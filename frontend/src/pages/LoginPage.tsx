import { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { Card } from "../components/Card";
import { useAuth } from "../hooks/useAuth";
import { ApiError } from "../services/api";

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const redirectTo = (location.state as { from?: string } | null)?.from ?? "/";

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      if (mode === "signup") {
        await auth.signup(email, password, displayName || undefined);
      } else {
        await auth.login(email, password);
      }
      navigate(redirectTo, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg)] px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <div className="text-[17px] font-semibold tracking-tight">Undertow</div>
          <div className="mt-1 text-[var(--text-small)] text-[var(--color-text-faint)]">
            Attention-aware market watchlist
          </div>
        </div>

        <Card>
          <div className="mb-5 flex gap-1 rounded-[var(--radius-sm)] bg-[var(--color-surface-sunken)] p-1">
            <button
              type="button"
              onClick={() => {
                setMode("login");
                setError(null);
              }}
              className={
                "flex-1 rounded-[var(--radius-sm)] py-1.5 text-[var(--text-small)] font-medium transition-colors " +
                (mode === "login" ? "bg-[var(--color-surface)] text-[var(--color-text)] shadow-[var(--shadow-xs)]" : "text-[var(--color-text-faint)]")
              }
            >
              Log in
            </button>
            <button
              type="button"
              onClick={() => {
                setMode("signup");
                setError(null);
              }}
              className={
                "flex-1 rounded-[var(--radius-sm)] py-1.5 text-[var(--text-small)] font-medium transition-colors " +
                (mode === "signup" ? "bg-[var(--color-surface)] text-[var(--color-text)] shadow-[var(--shadow-xs)]" : "text-[var(--color-text-faint)]")
              }
            >
              Sign up
            </button>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-3">
            {mode === "signup" && (
              <div>
                <label className="text-[var(--text-small)] font-medium text-[var(--color-text-muted)]">
                  Name <span className="text-[var(--color-text-faint)]">(optional)</span>
                </label>
                <input
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="mt-1 w-full rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-2 text-[var(--text-body)] outline-none focus:border-[var(--color-accent)]"
                  placeholder="Jane Doe"
                />
              </div>
            )}

            <div>
              <label className="text-[var(--text-small)] font-medium text-[var(--color-text-muted)]">Email</label>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 w-full rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-2 text-[var(--text-body)] outline-none focus:border-[var(--color-accent)]"
                placeholder="you@example.com"
              />
            </div>

            <div>
              <label className="text-[var(--text-small)] font-medium text-[var(--color-text-muted)]">Password</label>
              <input
                type="password"
                required
                minLength={mode === "signup" ? 8 : undefined}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-1 w-full rounded-[var(--radius-sm)] border border-[var(--color-border-strong)] px-3 py-2 text-[var(--text-body)] outline-none focus:border-[var(--color-accent)]"
                placeholder="••••••••"
              />
              {mode === "signup" && (
                <p className="mt-1 text-[var(--text-micro)] text-[var(--color-text-faint)]">At least 8 characters.</p>
              )}
            </div>

            {error && (
              <p className="rounded-[var(--radius-sm)] bg-[var(--color-negative)]/10 px-3 py-2 text-[var(--text-small)] text-[var(--color-negative)]">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="mt-1 rounded-[var(--radius-sm)] bg-[var(--color-accent)] px-3 py-2 text-[var(--text-body)] font-medium text-white hover:bg-[var(--color-accent-strong)] disabled:opacity-50"
            >
              {isSubmitting ? "Please wait…" : mode === "signup" ? "Create account" : "Log in"}
            </button>
          </form>
        </Card>
      </div>
    </div>
  );
}
