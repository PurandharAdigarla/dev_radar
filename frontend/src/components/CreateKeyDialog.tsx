import { useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import { Button } from "./Button";
import { Alert } from "./Alert";
import { monoStack } from "../theme";
import { useCreateApiKeyMutation } from "../api/apiKeyApi";
import type { ApiKeyScope, ApiKeyCreateResponse } from "../api/types";

interface CreateKeyDialogProps {
  open: boolean;
  onClose: () => void;
}

export function CreateKeyDialog({ open, onClose }: CreateKeyDialogProps) {
  const [name, setName] = useState("");
  const [scope, setScope] = useState<ApiKeyScope>("READ");
  const [created, setCreated] = useState<ApiKeyCreateResponse | null>(null);
  const [copied, setCopied] = useState(false);
  const [createKey, { isLoading, error }] = useCreateApiKeyMutation();

  function reset() {
    setName("");
    setScope("READ");
    setCreated(null);
    setCopied(false);
  }

  function handleClose() {
    reset();
    onClose();
  }

  async function handleCreate() {
    if (!name.trim()) return;
    const resp = await createKey({ name: name.trim(), scope }).unwrap().catch(() => null);
    if (resp) setCreated(resp);
  }

  async function handleCopy() {
    if (!created) return;
    await navigator.clipboard.writeText(created.key);
    setCopied(true);
  }

  if (created) {
    return (
      <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
        <DialogTitle sx={{ fontWeight: 500 }}>Key Created</DialogTitle>
        <DialogContent>
          <Typography sx={{ fontSize: "0.875rem", color: "text.secondary", mb: 2 }}>
            Copy this key now. You won't be able to see it again.
          </Typography>
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              p: "12px 16px",
              bgcolor: "rgba(45,42,38,0.03)",
              border: 1,
              borderColor: "divider",
              borderRadius: 1,
              fontFamily: monoStack,
              fontSize: "0.8125rem",
              wordBreak: "break-all",
            }}
          >
            <Box sx={{ flex: 1 }}>{created.key}</Box>
            <IconButton onClick={handleCopy} size="small" title="Copy to clipboard">
              <Typography sx={{ fontSize: "0.75rem" }}>{copied ? "✓" : "Copy"}</Typography>
            </IconButton>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button variant="outlined" onClick={handleClose}>Done</Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ fontWeight: 500 }}>Create API Key</DialogTitle>
      <DialogContent>
        {error && (
          <Box sx={{ mb: 2 }}>
            <Alert severity="error">Failed to create key. Try again.</Alert>
          </Box>
        )}
        <TextField
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          fullWidth
          placeholder="e.g. Claude Desktop"
          inputProps={{ maxLength: 100 }}
          sx={{ mb: 3, mt: 1 }}
        />
        <Typography sx={{ fontSize: "0.8125rem", fontWeight: 500, color: "text.primary", mb: 1 }}>
          Scope
        </Typography>
        <ToggleButtonGroup
          value={scope}
          exclusive
          onChange={(_e, v) => { if (v) setScope(v as ApiKeyScope); }}
          size="small"
        >
          <ToggleButton value="READ" sx={{ px: 3, textTransform: "none" }}>Read</ToggleButton>
          <ToggleButton value="WRITE" sx={{ px: 3, textTransform: "none" }}>Write</ToggleButton>
        </ToggleButtonGroup>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button variant="text" onClick={handleClose}>Cancel</Button>
        <Button onClick={handleCreate} disabled={!name.trim() || isLoading}>
          {isLoading ? "Creating…" : "Create"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
