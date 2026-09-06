import { useEffect, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { getStoredToken, setStoredToken, onUnauthorized } from "../services/api";
import { authApi } from "../services/endpoints";
import { AuthContext } from "./authContextObject";
import type { AuthUser } from "../types";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  // Only actually "loading" if there's a stored token to verify - with no
  // token there's nothing to check, so start already settled rather than
  // flipping isLoading true→false synchronously on mount for no reason.
  const [isLoading, setIsLoading] = useState(() => getStoredToken() !== null);
  const queryClient = useQueryClient();

  // On first load, if a token is already stored (page refresh), verify it's
  // still valid and restore the session rather than requiring the person to
  // log in again every time they reload.
  useEffect(() => {
    const token = getStoredToken();
    if (!token) return;
    authApi
      .me()
      .then((me) => setUser(me))
      .catch(() => setStoredToken(null))
      .finally(() => setIsLoading(false));
  }, []);

  // A 401 from any request (expired/invalid token) means the session is no
  // longer valid anywhere in the app - clear it centrally rather than
  // handling this ad hoc in every page that happens to hit it first.
  useEffect(() => {
    return onUnauthorized(() => {
      setStoredToken(null);
      setUser(null);
    });
  }, []);

  const signup = async (email: string, password: string, displayName?: string) => {
    const res = await authApi.signup(email, password, displayName);
    setStoredToken(res.token);
    setUser({ email: res.email, displayName: res.displayName });
  };

  const login = async (email: string, password: string) => {
    const res = await authApi.login(email, password);
    setStoredToken(res.token);
    setUser({ email: res.email, displayName: res.displayName });
  };

  const logout = () => {
    authApi.logout().catch(() => {
      // Best-effort server-side token cleanup - the local session is
      // cleared regardless, since staying "logged in" client-side after a
      // failed logout call would be worse than a token lingering server-side
      // until it naturally expires.
    });
    setStoredToken(null);
    setUser(null);
    // Every query in the cache belongs to whoever was just logged in - drop
    // it all so the next user (or a re-login) never briefly sees stale data.
    queryClient.clear();
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, signup, login, logout }}>{children}</AuthContext.Provider>
  );
}
