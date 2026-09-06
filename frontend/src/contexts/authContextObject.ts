import { createContext } from "react";
import type { AuthUser } from "../types";

export interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  signup: (email: string, password: string, displayName?: string) => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
