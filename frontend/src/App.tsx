import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
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
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Navigate to="radars" replace />} />
          <Route path="radars" element={<RadarListPage />} />
          <Route path="radars/:id" element={<RadarDetailPage />} />
          <Route path="interests" element={<InterestPickerPage />} />
          <Route path="settings/api-keys" element={<ApiKeysPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <Provider store={store}>
        <ThemeProvider theme={theme}>
          <CssBaseline />
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </ThemeProvider>
      </Provider>
    </ErrorBoundary>
  );
}
