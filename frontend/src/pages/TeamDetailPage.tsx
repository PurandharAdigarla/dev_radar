import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { TextField } from "../components/TextField";
import { RadarRow } from "../components/RadarRow";
import {
  useGetTeamQuery,
  useListMembersQuery,
  useAddMemberMutation,
  useRemoveMemberMutation,
  useListTeamRadarsQuery,
  useGenerateTeamRadarMutation,
  useLazySearchUserByEmailQuery,
} from "../api/teamApi";
import { useAuth } from "../auth/useAuth";

export function TeamDetailPage() {
  const params = useParams<{ teamId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const teamId = Number(params.teamId);

  const { data: team, isLoading: teamLoading, error: teamError } = useGetTeamQuery(teamId, { skip: !teamId });
  const { data: members = [] } = useListMembersQuery(teamId, { skip: !teamId });
  const { data: radars = [] } = useListTeamRadarsQuery(teamId, { skip: !teamId });

  const [addMember] = useAddMemberMutation();
  const [removeMember] = useRemoveMemberMutation();
  const [generateRadar, generateState] = useGenerateTeamRadarMutation();
  const [searchUser] = useLazySearchUserByEmailQuery();

  const [addEmail, setAddEmail] = useState("");
  const [addRole, setAddRole] = useState("MEMBER");
  const [showAddForm, setShowAddForm] = useState(false);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [inviteLoading, setInviteLoading] = useState(false);

  const isAdmin = members.some(
    (m) => m.userId === user?.id && (m.role === "OWNER" || m.role === "ADMIN")
  );

  async function onAddMember(e: React.FormEvent) {
    e.preventDefault();
    const email = addEmail.trim();
    if (!email) return;

    setInviteError(null);
    setInviteLoading(true);

    try {
      const result = await searchUser(email).unwrap();
      await addMember({ teamId, userId: result.id, role: addRole }).unwrap();
      setAddEmail("");
      setShowAddForm(false);
    } catch (err: unknown) {
      const apiErr = err as { status?: number; data?: { message?: string } };
      if (apiErr.status === 404) {
        setInviteError("No user found with that email address.");
      } else if (apiErr.status === 409) {
        setInviteError("That user is already a team member.");
      } else {
        setInviteError("Could not add member. Please try again.");
      }
    } finally {
      setInviteLoading(false);
    }
  }

  async function onRemoveMember(userId: number) {
    await removeMember({ teamId, userId }).unwrap().catch(() => null);
  }

  async function onGenerateRadar() {
    const created = await generateRadar(teamId).unwrap().catch(() => null);
    if (created) navigate(`/app/radars/${created.id}`);
  }

  if (teamLoading) {
    return (
      <Box sx={{ maxWidth: 960, width: "100%" }}>
        <Typography variant="body2" color="text.secondary">Loading team...</Typography>
      </Box>
    );
  }

  if (teamError || !team) {
    return (
      <Box sx={{ maxWidth: 960, width: "100%" }}>
        <Alert severity="error">Team not found or access denied.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title={team.name}
        sub={`${team.memberCount} member${team.memberCount !== 1 ? "s" : ""} · ${team.plan} plan`}
      />

      {/* Team radar section */}
      <Box sx={{ mb: 6 }}>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 3 }}>
          <Typography sx={{ fontSize: 16, fontWeight: 500, color: "text.primary" }}>
            Team Radars
          </Typography>
          <Button
            onClick={onGenerateRadar}
            disabled={generateState.isLoading}
          >
            {generateState.isLoading ? "Generating..." : "Generate team radar"}
          </Button>
        </Box>

        {generateState.isError && (
          <Box sx={{ mb: 3 }}>
            <Alert severity="error">Could not generate team radar. Try again.</Alert>
          </Box>
        )}

        {radars.length === 0 ? (
          <Box
            sx={{
              padding: "40px 24px",
              textAlign: "center",
              border: "1px dashed",
              borderColor: "divider",
              borderRadius: 3,
              color: "text.secondary",
              fontSize: 14,
            }}
          >
            No team radars yet. Generate one to see a shared brief from all members' interests.
          </Box>
        ) : (
          <Box sx={{ borderTop: 1, borderColor: "divider" }}>
            {radars.map((r) => (
              <RadarRow key={r.id} radar={r} />
            ))}
          </Box>
        )}
      </Box>

      {/* Members section */}
      <Box>
        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 3 }}>
          <Typography sx={{ fontSize: 16, fontWeight: 500, color: "text.primary" }}>
            Members
          </Typography>
          {isAdmin && !showAddForm && (
            <Button variant="outlined" onClick={() => setShowAddForm(true)}>
              Add member
            </Button>
          )}
        </Box>

        {inviteError && (
          <Box sx={{ mb: 3 }}>
            <Alert severity="error">{inviteError}</Alert>
          </Box>
        )}

        {showAddForm && (
          <Box
            component="form"
            onSubmit={onAddMember}
            sx={{
              mb: 3,
              p: "16px 20px",
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
                label="Email address"
                value={addEmail}
                onChange={(e) => { setAddEmail(e.target.value); setInviteError(null); }}
                placeholder="colleague@example.com"
                type="email"
                autoFocus
              />
            </Box>
            <Box sx={{ minWidth: 120 }}>
              <TextField
                label="Role"
                value={addRole}
                onChange={(e) => setAddRole(e.target.value)}
                select
                SelectProps={{ native: true }}
              >
                <option value="MEMBER">Member</option>
                <option value="ADMIN">Admin</option>
              </TextField>
            </Box>
            <Button type="submit" disabled={!addEmail.trim() || inviteLoading}>
              {inviteLoading ? "Adding..." : "Add"}
            </Button>
            <Button variant="text" onClick={() => { setShowAddForm(false); setAddEmail(""); setInviteError(null); }}>
              Cancel
            </Button>
          </Box>
        )}

        <Box sx={{ borderTop: 1, borderColor: "divider" }}>
          {members.map((m) => (
            <Box
              key={m.userId}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                borderBottom: 1,
                borderColor: "divider",
                padding: "12px 0",
              }}
            >
              <Box>
                <Typography sx={{ fontSize: 14, color: "text.primary" }}>
                  User #{m.userId}
                </Typography>
                <Typography sx={{ fontSize: 12, color: "text.secondary", mt: 0.25 }}>
                  {m.role} · Joined {new Date(m.joinedAt).toLocaleDateString()}
                </Typography>
              </Box>
              {isAdmin && m.role !== "OWNER" && (
                <Button
                  variant="text"
                  color="error"
                  onClick={() => onRemoveMember(m.userId)}
                  sx={{ fontSize: 13 }}
                >
                  Remove
                </Button>
              )}
            </Box>
          ))}
        </Box>
      </Box>
    </Box>
  );
}
