import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { PageHeader } from "../components/PageHeader";
import { Button } from "../components/Button";
import { ProGate } from "../components/ProGate";
import { CreateKeyDialog } from "../components/CreateKeyDialog";
import { RevokeKeyDialog } from "../components/RevokeKeyDialog";
import { monoStack, serifStack } from "../theme";
import { useListApiKeysQuery } from "../api/apiKeyApi";

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

export function ApiKeysPage() {
  const { data: keys, isLoading } = useListApiKeysQuery();
  const [createOpen, setCreateOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<{ id: number; name: string } | null>(null);

  const hasKeys = keys && keys.length > 0;

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title="API Keys"
        sub="Manage keys for MCP and API access."
      />

      <ProGate feature="API Keys">
        <Box sx={{ mb: 2, display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={() => setCreateOpen(true)}>Create key</Button>
        </Box>

        {isLoading && (
          <Typography variant="body2" color="text.secondary">Loading keys...</Typography>
        )}

        {!isLoading && !hasKeys && (
          <Box
            sx={{
              padding: "80px 24px",
              textAlign: "center",
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 3,
              bgcolor: "background.paper",
            }}
          >
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
              No API keys yet.
            </Box>
            <Typography sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}>
              Create one to use Dev Radar from Claude Desktop or Cursor.
            </Typography>
            <Button onClick={() => setCreateOpen(true)}>Create your first key</Button>
          </Box>
        )}

        {hasKeys && (
          <Box sx={{ borderTop: 1, borderColor: "divider" }}>
            {keys.map((k) => (
              <Box
                key={k.id}
                sx={{
                  display: "flex",
                  alignItems: "center",
                  gap: 2,
                  py: "14px",
                  px: 1,
                  borderBottom: 1,
                  borderColor: "divider",
                  flexWrap: "wrap",
                }}
              >
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography sx={{ fontSize: "0.9375rem", fontWeight: 500, color: "text.primary" }}>
                    {k.name}
                  </Typography>
                  <Box sx={{ display: "flex", gap: 2, mt: 0.5, flexWrap: "wrap" }}>
                    <Typography sx={{ fontFamily: monoStack, fontSize: "0.8125rem", color: "text.secondary" }}>
                      {k.keyPrefix}
                    </Typography>
                    <Typography
                      sx={{
                        fontSize: "0.6875rem",
                        textTransform: "uppercase",
                        letterSpacing: "0.06em",
                        fontWeight: 500,
                        color: k.scope === "WRITE" ? "text.primary" : "text.secondary",
                        bgcolor: "rgba(45,42,38,0.04)",
                        px: 1,
                        py: "2px",
                        borderRadius: 1,
                      }}
                    >
                      {k.scope}
                    </Typography>
                  </Box>
                </Box>
                <Box sx={{ textAlign: "right", flexShrink: 0 }}>
                  <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
                    Created {timeAgo(k.createdAt)}
                  </Typography>
                  <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
                    {k.lastUsedAt ? `Used ${timeAgo(k.lastUsedAt)}` : "Never used"}
                  </Typography>
                </Box>
                <Box
                  component="button"
                  onClick={() => setRevokeTarget({ id: k.id, name: k.name })}
                  sx={{
                    background: "transparent",
                    border: "none",
                    padding: "4px 8px",
                    fontFamily: "inherit",
                    fontSize: "0.8125rem",
                    color: "error.main",
                    cursor: "pointer",
                    borderRadius: 1,
                    "&:hover": { bgcolor: "rgba(179,38,30,0.04)" },
                  }}
                >
                  Revoke
                </Box>
              </Box>
            ))}
          </Box>
        )}

        <CreateKeyDialog open={createOpen} onClose={() => setCreateOpen(false)} />
        <RevokeKeyDialog
          open={revokeTarget !== null}
          keyId={revokeTarget?.id ?? null}
          keyName={revokeTarget?.name ?? ""}
          onClose={() => setRevokeTarget(null)}
        />
      </ProGate>
    </Box>
  );
}
