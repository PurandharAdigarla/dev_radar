import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { useRevokeApiKeyMutation } from "../api/apiKeyApi";

interface RevokeKeyDialogProps {
  open: boolean;
  keyId: number | null;
  keyName: string;
  onClose: () => void;
}

export function RevokeKeyDialog({ open, keyId, keyName, onClose }: RevokeKeyDialogProps) {
  const [revoke, { isLoading, error }] = useRevokeApiKeyMutation();

  async function handleRevoke() {
    if (keyId == null) return;
    try {
      await revoke(keyId).unwrap();
      onClose();
    } catch {
      // RTK Query sets `error` state — dialog stays open so user sees the Alert
    }
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 500 }}>Revoke Key</DialogTitle>
      <DialogContent>
        {error && (
          <Box sx={{ mb: 2 }}>
            <Alert severity="error">Failed to revoke key.</Alert>
          </Box>
        )}
        <Typography sx={{ fontSize: "0.875rem", color: "text.secondary" }}>
          Revoke key "{keyName}"? Any integrations using this key will stop working.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="text" onClick={onClose}>Cancel</Button>
        <Button color="error" onClick={handleRevoke} disabled={isLoading}>
          {isLoading ? "Revoking…" : "Revoke"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
