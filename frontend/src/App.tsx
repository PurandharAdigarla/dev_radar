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
import { RadarListPage } from "./pages/RadarListPage";
import { RadarDetailPage } from "./pages/RadarDetailPage";
import { ThemeDetailPage } from "./pages/ThemeDetailPage";
import { SharedRadarPage } from "./pages/SharedRadarPage";
import { PublicStackRadarPage } from "./pages/PublicStackRadarPage";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { ErrorBoundary } from "./ErrorBoundary";

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/radar/shared/:shareToken" element={<SharedRadarPage />} />
      <Route path="/radar/:tagSlug/week/:weekNumber" element={<PublicStackRadarPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppShell />}>
          <Route index element={<Navigate to="radars" replace />} />
          <Route path="radars" element={<RadarListPage />} />
          <Route path="radars/:id" element={<RadarDetailPage />} />
          <Route path="radars/:id/themes/:themeId" element={<ThemeDetailPage />} />
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
