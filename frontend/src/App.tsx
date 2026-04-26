import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import { store } from "./store";
import { theme } from "./theme";
import { Landing } from "./pages/Landing";
import { Login } from "./pages/Login";
import { Register } from "./pages/Register";
import { AppShell } from "./pages/AppShell";
import { InterestPickerPage } from "./pages/InterestPickerPage";
import { RadarListPage } from "./pages/RadarListPage";
import { RadarDetailPage } from "./pages/RadarDetailPage";
import { GitHubCallback } from "./pages/GitHubCallback";
import { ObservabilityPage } from "./pages/ObservabilityPage";
import { ApiKeysPage } from "./pages/ApiKeysPage";
import { SettingsPage } from "./pages/SettingsPage";
import { NotificationsPage } from "./pages/NotificationsPage";
import { TeamDashboardPage } from "./pages/TeamDashboardPage";
import { TeamDetailPage } from "./pages/TeamDetailPage";
import { SharedRadarPage } from "./pages/SharedRadarPage";
import { DashboardPage } from "./pages/DashboardPage";
import { OnboardingPage } from "./pages/OnboardingPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ErrorBoundary } from "./ErrorBoundary";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/auth/github/complete" element={<GitHubCallback />} />
      <Route path="/observability" element={<ObservabilityPage />} />
      <Route path="/radar/shared/:shareToken" element={<SharedRadarPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app/onboarding" element={<OnboardingPage />} />
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="radars" element={<RadarListPage />} />
          <Route path="radars/:id" element={<RadarDetailPage />} />
          <Route path="interests" element={<InterestPickerPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="settings/api-keys" element={<ApiKeysPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="teams" element={<TeamDashboardPage />} />
          <Route path="teams/:teamId" element={<TeamDetailPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function LocationAwareErrorBoundary({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  return <ErrorBoundary resetKey={location.pathname}>{children}</ErrorBoundary>;
}

export default function App() {
  return (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <LocationAwareErrorBoundary>
            <AppRoutes />
          </LocationAwareErrorBoundary>
        </BrowserRouter>
      </ThemeProvider>
    </Provider>
  );
}
