import { useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { TextField } from "../components/TextField";
import { useListTeamsQuery, useCreateTeamMutation } from "../api/teamApi";

export function TeamDashboardPage() {
  const navigate = useNavigate();
  const { data: teams, isLoading } = useListTeamsQuery();
  const [createTeam, createState] = useCreateTeamMutation();
  const [newName, setNewName] = useState("");
  const [showForm, setShowForm] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!newName.trim()) return;
    const created = await createTeam({ name: newName.trim() }).unwrap().catch(() => null);
    if (created) {
      setNewName("");
      setShowForm(false);
      navigate(`/app/teams/${created.id}`);
    }
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title="Teams"
        sub="Collaborate with your team on shared radars."
        right={
          !showForm ? (
            <Button onClick={() => setShowForm(true)}>Create team</Button>
          ) : undefined
        }
      />

      {createState.isError && (
        <Box sx={{ mb: 4 }}>
          <Alert severity="error">Could not create team. Try again.</Alert>
        </Box>
      )}

      {showForm && (
        <Box
          component="form"
          onSubmit={onSubmit}
          sx={{
            mb: 4,
            p: "20px 24px",
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 1,
            display: "flex",
            alignItems: "flex-end",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Box sx={{ flex: 1, minWidth: 200 }}>
            <TextField
              label="Team name"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="e.g. Platform Engineering"
              fullWidth
              autoFocus
            />
          </Box>
          <Button type="submit" disabled={!newName.trim() || createState.isLoading}>
            {createState.isLoading ? "Creating..." : "Create"}
          </Button>
          <Button
            variant="text"
            onClick={() => {
              setShowForm(false);
              setNewName("");
            }}
          >
            Cancel
          </Button>
        </Box>
      )}

      {isLoading && (
        <Typography variant="body2" color="text.secondary">
          Loading teams...
        </Typography>
      )}

      {!isLoading && (!teams || teams.length === 0) && !showForm && (
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
          <Typography sx={{ fontSize: 16, color: "text.primary", mb: 1 }}>
            No teams yet.
          </Typography>
          <Typography sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}>
            Create a team to generate shared radars with your colleagues.
          </Typography>
          <Button onClick={() => setShowForm(true)}>Create your first team</Button>
        </Box>
      )}

      {teams && teams.length > 0 && (
        <Box sx={{ borderTop: 1, borderColor: "divider" }}>
          {teams.map((t) => (
            <Box
              key={t.id}
              component="button"
              onClick={() => navigate(`/app/teams/${t.id}`)}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                width: "100%",
                background: "transparent",
                border: "none",
                borderBottom: 1,
                borderColor: "divider",
                padding: "16px 0",
                cursor: "pointer",
                fontFamily: "inherit",
                textAlign: "left",
                "&:hover": { bgcolor: "rgba(45,42,38,0.02)" },
              }}
            >
              <Box>
                <Typography sx={{ fontSize: 15, fontWeight: 500, color: "text.primary" }}>
                  {t.name}
                </Typography>
                <Typography sx={{ fontSize: 13, color: "text.secondary", mt: 0.5 }}>
                  {t.memberCount} member{t.memberCount !== 1 ? "s" : ""} · {t.plan}
                </Typography>
              </Box>
              <Typography sx={{ fontSize: 13, color: "text.secondary" }}>
                →
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </Box>
  );
}
