import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { ProposalApproveModal } from "./ProposalApproveModal";
import { monoStack } from "../theme";
import { useApproveProposalMutation, useDismissProposalMutation } from "../api/actionApi";
import type { ActionProposal, CveFixPayload } from "../api/types";

function parsePayload(json: string): Partial<CveFixPayload> {
  try { return JSON.parse(json) as Partial<CveFixPayload>; }
  catch { return {}; }
}

function ArrowIcon() {
  return (
    <Box
      component="svg"
      width="14"
      height="10"
      viewBox="0 0 14 10"
      fill="none"
      aria-hidden="true"
      sx={{ flexShrink: 0, width: 14, height: 10, opacity: 0.5 }}
    >
      <path
        d="M1 5h12M9 1l4 4-4 4"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Box>
  );
}

function CheckIcon() {
  return (
    <Box
      component="svg"
      width="14"
      height="14"
      viewBox="0 0 14 14"
      fill="none"
      aria-hidden="true"
      sx={{ flexShrink: 0, width: 14, height: 14 }}
    >
      <path
        d="M2 7.5l3 3 7-7"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </Box>
  );
}

export interface ProposalCardProps {
  proposal: ActionProposal;
}

export function ProposalCard({ proposal }: ProposalCardProps) {
  const payload = parsePayload(proposal.payloadJson);
  const [modalOpen, setModalOpen] = useState(false);
  const [approve] = useApproveProposalMutation();
  const [dismiss, dismissState] = useDismissProposalMutation();

  const isProposed = proposal.status === "PROPOSED";
  const isExecuted = proposal.status === "EXECUTED";
  const isDismissed = proposal.status === "DISMISSED";
  const isFailed = proposal.status === "FAILED";

  async function handleApprove(fixVersion: string) {
    await approve({ id: proposal.id, fixVersion }).unwrap();
    setModalOpen(false);
  }

  async function handleRetry() {
    if (payload.fixVersion) {
      await approve({ id: proposal.id, fixVersion: payload.fixVersion }).unwrap().catch(() => {});
    }
  }

  async function handleDismiss() {
    await dismiss(proposal.id).unwrap().catch(() => {});
  }

  return (
    <Box
      sx={{
        padding: 2,
        bgcolor: "background.paper",
        border: 1,
        borderColor: "divider",
        borderRadius: 1,
        mb: "12px",
        opacity: isDismissed ? 0.5 : 1,
        transition: "opacity 200ms",
      }}
    >
      <Box
        sx={{
          fontFamily: monoStack,
          fontSize: 11,
          letterSpacing: "0.04em",
          color: "text.secondary",
          mb: "6px",
        }}
      >
        {payload.cveId ?? "CVE"}
      </Box>
      <Typography
        sx={{
          fontSize: 14,
          lineHeight: "20px",
          fontWeight: 500,
          color: "text.primary",
          mb: "10px",
        }}
      >
        {payload.packageName ?? "package"}
      </Typography>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          fontFamily: monoStack,
          fontSize: 13,
          color: "text.primary",
          mb: 2,
        }}
      >
        <Box component="span" sx={{ color: "text.secondary" }}>
          {payload.currentVersion ?? "—"}
        </Box>
        <ArrowIcon />
        <Box component="span" sx={{ fontWeight: 500 }}>
          {payload.fixVersion ?? "—"}
        </Box>
      </Box>

      {isFailed && (
        <Box sx={{ mb: 1.5 }}>
          <Alert severity="error">{proposal.failureReason ?? "PR creation failed."}</Alert>
        </Box>
      )}

      {isProposed && (
        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
          <Button size="small" onClick={() => setModalOpen(true)}>
            Approve
          </Button>
          <Button variant="text" onClick={handleDismiss} disabled={dismissState.isLoading}>
            Dismiss
          </Button>
        </Box>
      )}

      {isExecuted && proposal.prUrl && (
        <Box
          component="a"
          href={proposal.prUrl}
          target="_blank"
          rel="noreferrer noopener"
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            fontSize: 13,
            fontWeight: 500,
            color: "success.main",
            textDecoration: "none",
          }}
        >
          <CheckIcon />
          PR opened →
        </Box>
      )}

      {isDismissed && (
        <Typography sx={{ fontSize: 13, color: "text.secondary" }}>Dismissed</Typography>
      )}

      {isFailed && (
        <Button variant="outlined" size="small" onClick={handleRetry}>
          Retry
        </Button>
      )}

      <ProposalApproveModal
        open={modalOpen}
        context={{
          cveId: payload.cveId ?? "CVE",
          packageName: payload.packageName ?? "package",
          fromVersion: payload.currentVersion ?? "—",
        }}
        initialFixVersion={payload.fixVersion ?? ""}
        onCancel={() => setModalOpen(false)}
        onSubmit={handleApprove}
      />
    </Box>
  );
}
