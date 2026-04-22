import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Provider } from "react-redux";
import { ThemeProvider } from "@mui/material/styles";
import { makeStore } from "../store";
import { theme } from "../theme";
import { ProposalCard } from "./ProposalCard";
import { tokenStorage } from "../auth/tokenStorage";
import type { ActionProposal } from "../api/types";

function setup(proposal: ActionProposal) {
  tokenStorage.setAccess("valid-token");
  render(
    <Provider store={makeStore()}>
      <ThemeProvider theme={theme}>
        <ProposalCard proposal={proposal} />
      </ThemeProvider>
    </Provider>,
  );
  return { user: userEvent.setup() };
}

const PROPOSED: ActionProposal = {
  id: 7, radarId: 42, kind: "CVE_FIX_PR",
  payloadJson: JSON.stringify({
    cveId: "CVE-2024-1234", packageName: "jackson-databind",
    currentVersion: "2.16.1", fixVersion: "2.17.0",
    repoOwner: "alice", repoName: "api",
  }),
  status: "PROPOSED", prUrl: null, failureReason: null,
  createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:00:00Z",
};

describe("ProposalCard", () => {
  it("renders CVE id, package, and version bump", () => {
    setup(PROPOSED);
    expect(screen.getByText(/CVE-2024-1234/)).toBeInTheDocument();
    expect(screen.getByText(/jackson-databind/)).toBeInTheDocument();
    expect(screen.getByText(/2\.16\.1/)).toBeInTheDocument();
    expect(screen.getByText(/2\.17\.0/)).toBeInTheDocument();
  });

  it("opens approve modal on Approve click", async () => {
    const { user } = setup(PROPOSED);
    await user.click(screen.getByRole("button", { name: /approve/i }));
    expect(screen.getByRole("dialog", { name: /open migration pr/i })).toBeInTheDocument();
    // Version field prefilled
    expect(screen.getByDisplayValue("2.17.0")).toBeInTheDocument();
  });

  it("shows PR link when executed", () => {
    setup({
      ...PROPOSED,
      status: "EXECUTED",
      prUrl: "https://github.com/alice/api/pull/99",
    });
    expect(screen.getByRole("link", { name: /pr opened/i })).toHaveAttribute(
      "href",
      "https://github.com/alice/api/pull/99",
    );
  });

  it("shows failure reason on FAILED", () => {
    setup({ ...PROPOSED, status: "FAILED", failureReason: "Branch name already exists" });
    expect(screen.getByText(/branch name already exists/i)).toBeInTheDocument();
  });
});
