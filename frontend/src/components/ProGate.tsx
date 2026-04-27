import type { ReactNode } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "./Button";
import { serifStack } from "../theme";
import { useGetPlanQuery, useStartTrialMutation } from "../api/planApi";

export interface ProGateProps {
  children: ReactNode;
  feature: string;
}

const FEATURE_DESCRIPTIONS: Record<string, string> = {
  "API Keys": "Create API keys to integrate Dev Radar with Claude Desktop, Cursor, and other tools.",
  "Auto-fix PRs": "Automatically open pull requests to fix known CVE vulnerabilities in your dependencies.",
};

function LockIcon() {
  return (
    <Box
      component="svg"
      width="28"
      height="28"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      sx={{ color: "text.secondary" }}
    >
      <rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="M8 11V7a4 4 0 1 1 8 0v4"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </Box>
  );
}

export function ProGate({ children, feature }: ProGateProps) {
  const { data: plan, isLoading } = useGetPlanQuery();
  const [startTrial, trialState] = useStartTrialMutation();

  if (isLoading) return null;

  const hasAccess =
    plan &&
    (plan.plan === "PRO" || plan.plan === "TEAM" || plan.trialActive);

  if (hasAccess) return <>{children}</>;

  const description =
    FEATURE_DESCRIPTIONS[feature] ?? `${feature} is a Pro feature.`;
  const trialUsed = plan?.trialUsed ?? false;

  return (
    <Box
      sx={{
        padding: "48px 24px",
        textAlign: "center",
        border: "1px dashed",
        borderColor: "divider",
        borderRadius: 3,
        bgcolor: "background.paper",
      }}
    >
      <Box sx={{ mb: 2, display: "flex", justifyContent: "center" }}>
        <LockIcon />
      </Box>
      <Box
        sx={{
          fontFamily: serifStack,
          fontSize: 20,
          lineHeight: "28px",
          fontStyle: "italic",
          color: "text.primary",
          mb: 1,
        }}
      >
        {feature} requires Pro
      </Box>
      <Typography
        sx={{
          fontSize: 14,
          color: "text.secondary",
          mb: 3,
          lineHeight: "22px",
          maxWidth: 400,
          mx: "auto",
        }}
      >
        {description}
      </Typography>
      {!trialUsed ? (
        <Button
          onClick={() => startTrial()}
          disabled={trialState.isLoading}
        >
          {trialState.isLoading ? "Starting trial..." : "Start 14-day free trial"}
        </Button>
      ) : (
        <Button disabled>Trial already used</Button>
      )}
      {trialState.isError && (
        <Typography sx={{ fontSize: 13, color: "error.main", mt: 1.5 }}>
          Failed to start trial. Please try again.
        </Typography>
      )}
    </Box>
  );
}
