import { Routes, Route } from "react-router-dom";
import { AppShell } from "./components/AppShell";
import { RequireAuth } from "./components/RequireAuth";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { WatchlistPage } from "./pages/WatchlistPage";
import { SinceLastCheckedPage } from "./pages/SinceLastCheckedPage";
import { SignalDetailPage } from "./pages/SignalDetailPage";
import { AttentionDebtPage } from "./pages/AttentionDebtPage";
import { ReplayPage } from "./pages/ReplayPage";
import { SettingsPage } from "./pages/SettingsPage";
import { DataStatusPage } from "./pages/DataStatusPage";
import { EvidenceDrawerProvider } from "./contexts/EvidenceDrawerContext";
import { AuthProvider } from "./contexts/AuthContext";

export default function App() {
  return (
    <AuthProvider>
      <EvidenceDrawerProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route element={<AppShell />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/watchlist" element={<WatchlistPage />} />
              <Route path="/watchlist/:watchlistId" element={<WatchlistPage />} />
              <Route path="/since-last-checked" element={<SinceLastCheckedPage />} />
              <Route path="/signals/:signalId" element={<SignalDetailPage />} />
              <Route path="/attention-debt" element={<AttentionDebtPage />} />
              <Route path="/replay" element={<ReplayPage />} />
              <Route path="/settings" element={<SettingsPage />} />
              <Route path="/data-status" element={<DataStatusPage />} />
            </Route>
          </Route>
        </Routes>
      </EvidenceDrawerProvider>
    </AuthProvider>
  );
}
