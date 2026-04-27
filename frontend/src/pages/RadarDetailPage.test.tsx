import { describe, it, expect, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { RadarDetailPage } from "./RadarDetailPage";
import { tokenStorage } from "../auth/tokenStorage";
import { installMockEventSource, MockEventSource } from "../test/eventSourceMock";

function setup(initialPath = "/app/radars/42") {
  tokenStorage.setAccess("valid-token");
  installMockEventSource();
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <MemoryRouter initialEntries={[initialPath]}>
          <Routes>
            <Route path="/app/radars/:id" element={<RadarDetailPage />} />
            <Route path="/app/radars" element={<div>list-page</div>} />
          </Routes>
        </MemoryRouter>
      </ThemeProvider>
    </Provider>,
  );
}

describe("RadarDetailPage", () => {
  afterEach(() => MockEventSource.reset());

  it("renders persisted themes for a READY radar", async () => {
    setup();
    await waitFor(() =>
      expect(screen.getByText(/Spring Boot ecosystem updates/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/summary text/i)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /this week in your stack/i, level: 1 })).toBeInTheDocument();
  });

  it("redirects to list on 404", async () => {
    setup("/app/radars/404");
    await waitFor(() => expect(screen.getByText("list-page")).toBeInTheDocument());
  });

  it("renders action proposals panel when proposals exist", async () => {
    setup();
    await waitFor(() => expect(screen.getByText(/GHSA-xxxx-yyyy-zzzz/)).toBeInTheDocument());
    expect(await screen.findByRole("button", { name: /approve/i })).toBeInTheDocument();
  });
});
