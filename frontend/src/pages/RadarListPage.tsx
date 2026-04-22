import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { PageHeader } from "../components/PageHeader";
import { RadarRow } from "../components/RadarRow";
import { serifStack } from "../theme";
import { useCreateRadarMutation, useListRadarsQuery } from "../api/radarApi";
import { useGetMyInterestsQuery } from "../api/interestApi";

export function RadarListPage() {
  const navigate = useNavigate();
  const { data: page, isLoading } = useListRadarsQuery({ page: 0, size: 20 });
  const { data: interests } = useGetMyInterestsQuery();
  const [createRadar, createState] = useCreateRadarMutation();

  const hasInterests = (interests?.length ?? 0) > 0;
  const radars = page?.content ?? [];
  const hasRadars = radars.length > 0;

  async function onGenerate() {
    if (!hasInterests) return;
    const created = await createRadar().unwrap().catch(() => null);
    if (created) navigate(`/app/radars/${created.id}`);
  }

  return (
    <Box sx={{ maxWidth: 960, width: "100%" }}>
      <PageHeader
        title="Radars"
        sub="Your weekly briefs."
        right={
          <Button
            onClick={onGenerate}
            disabled={!hasInterests || createState.isLoading}
            title={hasInterests ? undefined : "Pick at least one interest first"}
          >
            {createState.isLoading ? "Starting…" : "Generate new radar"}
          </Button>
        }
      />

      {createState.isError && (
        <Box sx={{ mb: 4 }}>
          <Alert severity="error">Couldn't start a new radar. Try again.</Alert>
        </Box>
      )}

      {!hasInterests && interests !== undefined && (
        <Box
          sx={{
            mb: 4,
            padding: "16px 20px",
            bgcolor: "rgba(45,42,38,0.03)",
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 2,
            flexWrap: "wrap",
          }}
        >
          <Typography sx={{ fontSize: 14, lineHeight: "22px", color: "text.primary" }}>
            Pick a few interests to generate your first radar.
          </Typography>
          <Box
            component="button"
            onClick={() => navigate("/app/interests")}
            sx={{
              background: "transparent",
              border: "none",
              padding: 0,
              fontFamily: "inherit",
              fontSize: 14,
              fontWeight: 500,
              color: "text.primary",
              cursor: "pointer",
              textDecoration: "underline",
              textUnderlineOffset: "3px",
            }}
          >
            Pick interests →
          </Box>
        </Box>
      )}

      {isLoading && (
        <Typography variant="body2" color="text.secondary">Loading radars…</Typography>
      )}

      {!isLoading && !hasRadars && hasInterests && (
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
            No radars yet.
          </Box>
          <Typography
            sx={{ fontSize: 14, color: "text.secondary", mb: 3, lineHeight: "22px" }}
          >
            Generate one to see how the weekly brief comes together.
          </Typography>
          <Button onClick={onGenerate} disabled={createState.isLoading}>
            Generate your first radar
          </Button>
        </Box>
      )}

      {hasRadars && (
        <Box sx={{ borderTop: 1, borderColor: "divider" }}>
          {radars.map((r) => (
            <RadarRow key={r.id} radar={r} />
          ))}
        </Box>
      )}
    </Box>
  );
}
