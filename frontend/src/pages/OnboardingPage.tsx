import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Step,
  StepLabel,
  Stepper,
  Typography,
  Alert,
} from "@mui/material";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import GitHubIcon from "@mui/icons-material/GitHub";
import {
  useScanReposMutation,
  useApplyInterestsMutation,
  type ScanResult,
} from "../api/onboardingApi";

const steps = ["Scan Repos", "Choose Interests", "Generate Radar"];

export function OnboardingPage() {
  const navigate = useNavigate();
  const [activeStep, setActiveStep] = useState(0);
  const [scanResult, setScanResult] = useState<ScanResult | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [scanRepos, { isLoading: scanning, error: scanError }] = useScanReposMutation();
  const [applyInterests, { isLoading: applying }] = useApplyInterestsMutation();

  const handleScan = async () => {
    try {
      const result = await scanRepos().unwrap();
      setScanResult(result);
      setSelected(new Set(result.detectedInterests));
      setActiveStep(1);
    } catch {
      // error shown via scanError
    }
  };

  const toggleInterest = (slug: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });
  };

  const handleApply = async () => {
    if (selected.size === 0) return;
    await applyInterests({ tagSlugs: Array.from(selected) }).unwrap();
    setActiveStep(2);
    setTimeout(() => navigate("/app/radars"), 1500);
  };

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Typography variant="h4" fontWeight={700} gutterBottom textAlign="center">
        Welcome to Dev Radar
      </Typography>
      <Typography color="text.secondary" textAlign="center" mb={4}>
        Let&apos;s detect your tech stack and generate your first radar.
      </Typography>

      <Stepper activeStep={activeStep} sx={{ mb: 4 }}>
        {steps.map((label) => (
          <Step key={label}>
            <StepLabel>{label}</StepLabel>
          </Step>
        ))}
      </Stepper>

      <Paper sx={{ p: 4, textAlign: "center" }}>
        {activeStep === 0 && (
          <Box>
            <GitHubIcon sx={{ fontSize: 64, color: "text.secondary", mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              Scan your GitHub repos
            </Typography>
            <Typography color="text.secondary" mb={3}>
              We&apos;ll analyze your repositories to detect languages, frameworks,
              and topics you work with.
            </Typography>
            {scanError && (
              <Alert severity="error" sx={{ mb: 2, textAlign: "left" }}>
                {"data" in scanError
                  ? (scanError.data as { message?: string })?.message || "Scan failed"
                  : "Network error — please try again"}
              </Alert>
            )}
            <Button
              variant="contained"
              size="large"
              startIcon={scanning ? <CircularProgress size={20} /> : <GitHubIcon />}
              onClick={handleScan}
              disabled={scanning}
            >
              {scanning ? "Scanning…" : "Scan My GitHub Repos"}
            </Button>
          </Box>
        )}

        {activeStep === 1 && scanResult && (
          <Box>
            <Typography variant="h6" gutterBottom>
              We found {scanResult.repoCount} repos
            </Typography>
            <Typography color="text.secondary" mb={2}>
              Toggle the interests that matter to you:
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, justifyContent: "center", mb: 3 }}>
              {scanResult.detectedInterests.map((slug) => (
                <Chip
                  key={slug}
                  label={slug.replace(/_/g, " ")}
                  color={selected.has(slug) ? "primary" : "default"}
                  variant={selected.has(slug) ? "filled" : "outlined"}
                  onClick={() => toggleInterest(slug)}
                />
              ))}
            </Box>
            {scanResult.topRepos.length > 0 && (
              <Box sx={{ mb: 3, textAlign: "left" }}>
                <Typography variant="subtitle2" gutterBottom>
                  Top repos scanned:
                </Typography>
                {scanResult.topRepos.map((r) => (
                  <Typography key={r.name} variant="body2" color="text.secondary">
                    {r.name} {r.language ? `· ${r.language}` : ""}
                  </Typography>
                ))}
              </Box>
            )}
            <Button
              variant="contained"
              size="large"
              onClick={handleApply}
              disabled={applying || selected.size === 0}
              startIcon={applying ? <CircularProgress size={20} /> : <RocketLaunchIcon />}
            >
              {applying ? "Saving…" : `Continue with ${selected.size} interests`}
            </Button>
          </Box>
        )}

        {activeStep === 2 && (
          <Box>
            <RocketLaunchIcon sx={{ fontSize: 64, color: "primary.main", mb: 2 }} />
            <Typography variant="h6" gutterBottom>
              You&apos;re all set!
            </Typography>
            <Typography color="text.secondary">
              Redirecting to generate your first radar…
            </Typography>
            <CircularProgress sx={{ mt: 2 }} />
          </Box>
        )}
      </Paper>
    </Container>
  );
}
