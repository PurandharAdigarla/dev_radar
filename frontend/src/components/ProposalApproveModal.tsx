import { useState, type FormEvent } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import { Button } from "./Button";
import { TextField } from "./TextField";
import { Alert } from "./Alert";
import { PulseDot } from "./PulseDot";
import { monoStack } from "../theme";

export interface ProposalApproveContext {
  cveId: string;
  packageName: string;
  fromVersion: string;
}

export interface ProposalApproveModalProps {
  open: boolean;
  context: ProposalApproveContext;
  initialFixVersion: string;
  onCancel: () => void;
  onSubmit: (fixVersion: string) => Promise<void>;
}

export function ProposalApproveModal(props: ProposalApproveModalProps) {
  const { open, context, initialFixVersion, onCancel, onSubmit } = props;
  const [fixVersion, setFixVersion] = useState(initialFixVersion);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(fixVersion);
    } catch (err) {
      const msg = (err as { data?: { message?: string } }).data?.message;
      setError(msg ?? "Could not open PR. Try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={submitting ? undefined : onCancel}
      maxWidth="xs"
      fullWidth
      aria-labelledby="approve-pr-title"
      PaperProps={{
        sx: {
          borderRadius: 3,
          boxShadow: "0 1px 2px rgba(45,42,38,0.04), 0 12px 40px rgba(45,42,38,0.15)",
        },
      }}
    >
      <DialogTitle
        id="approve-pr-title"
        sx={{ fontSize: 20, lineHeight: "28px", fontWeight: 500, letterSpacing: "-0.01em" }}
      >
        Open migration PR
      </DialogTitle>
      <Box component="form" onSubmit={handleSubmit}>
        <DialogContent>
          <Typography
            sx={{ fontSize: 14, lineHeight: "22px", color: "text.secondary", mb: 3 }}
          >
            This will push a branch to your GitHub repo and open a PR. You can review it before merging.
          </Typography>

          <Box
            sx={{
              padding: "12px 14px",
              bgcolor: "rgba(45,42,38,0.03)",
              borderRadius: 1,
              mb: 2.5,
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              gap: 1.5,
              fontSize: 13,
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Box sx={{ fontFamily: monoStack, fontSize: 11, color: "text.secondary" }}>
                {context.cveId}
              </Box>
              <Box sx={{ fontWeight: 500, mt: "2px" }}>{context.packageName}</Box>
            </Box>
            <Box sx={{ fontFamily: monoStack, color: "text.secondary" }}>
              {context.fromVersion}
            </Box>
          </Box>

          {error && (
            <Box sx={{ mb: 2.5 }}>
              <Alert severity="error">{error}</Alert>
            </Box>
          )}

          <TextField
            label="Upgrade to version"
            value={fixVersion}
            onChange={(e) => setFixVersion(e.target.value)}
            disabled={submitting}
            required
            autoFocus
            InputProps={{ sx: { fontFamily: monoStack } }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button variant="text" onClick={onCancel} disabled={submitting}>Cancel</Button>
          <Button
            type="submit"
            disabled={submitting || !fixVersion.trim()}
            startIcon={submitting ? <PulseDot size={6} color="#ffffff" /> : undefined}
          >
            {submitting ? "Opening PR…" : "Open PR"}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
