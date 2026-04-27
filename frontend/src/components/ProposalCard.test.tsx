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
  id: 7, radarId: 42, kind: "auto_pr_cve",
  payloadJson: JSON.stringify({
    ghsa_id: "GHSA-xxxx-yyyy-zzzz", package: "jackson-databind",
    current_version: "2.16.1", repo: "alice/api",
    file_path: "pom.xml", file_sha: "abc123",
  }),
  status: "PROPOSED", prUrl: null, failureReason: null,
  createdAt: "2026-04-20T10:00:00Z", updatedAt: "2026-04-20T10:00:00Z",
};

describe("ProposalCard", () => {
  it("renders GHSA id, package, and current version", () => {
    setup(PROPOSED);
    expect(screen.getByText(/GHSA-xxxx-yyyy-zzzz/)).toBeInTheDocument();
    expect(screen.getByText(/jackson-databind/)).toBeInTheDocument();
    expect(screen.getByText(/2\.16\.1/)).toBeInTheDocument();
  });

  it("opens approve modal on Approve click", async () => {
    const { user } = setup(PROPOSED);
    const btn = await screen.findByRole("button", { name: /approve/i });
    await user.click(btn);
    expect(screen.getByRole("dialog", { name: /open migration pr/i })).toBeInTheDocument();
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
