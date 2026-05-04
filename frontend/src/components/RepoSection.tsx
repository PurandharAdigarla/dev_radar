import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type { RepoRecommendation } from "../api/types";

const CATEGORY_LABELS: Record<string, string> = {
  "mcp-server": "MCP Server",
  "agent-skill": "Agent Skill",
  "agent-framework": "Agent Framework",
  "dev-tool": "Dev Tool",
  "prompt-library": "Prompt Library",
};

function RepoCard({ repo }: { repo: RepoRecommendation }) {
  return (
    <Box
      component="a"
      href={repo.repoUrl}
      target="_blank"
      rel="noopener noreferrer"
      sx={{
        display: "block",
        p: 2.5,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        bgcolor: "background.paper",
        textDecoration: "none",
        color: "text.primary",
        transition: "border-color 120ms, box-shadow 120ms",
        "&:hover": {
          borderColor: "text.secondary",
          boxShadow: "0 1px 3px rgba(45,42,38,0.06)",
        },
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.75 }}>
        <Typography
          sx={{
            fontSize: "0.9375rem",
            fontWeight: 500,
            color: "text.primary",
            flex: 1,
            minWidth: 0,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {repo.repoName}
        </Typography>
        <Typography
          sx={{
            fontSize: "0.6875rem",
            fontWeight: 500,
            textTransform: "uppercase",
            letterSpacing: "0.04em",
            color: "text.secondary",
            px: 1,
            py: 0.25,
            border: "1px solid",
            borderColor: "divider",
            borderRadius: 1,
            whiteSpace: "nowrap",
            flexShrink: 0,
          }}
        >
          {CATEGORY_LABELS[repo.category] ?? repo.category}
        </Typography>
      </Box>

      {repo.description && (
        <Typography
          sx={{
            fontSize: "0.8125rem",
            color: "text.secondary",
            lineHeight: 1.5,
            mb: 1,
          }}
        >
          {repo.description}
        </Typography>
      )}

      <Typography
        sx={{
          fontSize: "0.8125rem",
          color: "text.primary",
          lineHeight: 1.5,
        }}
      >
        {repo.whyNotable}
      </Typography>

      <Typography
        sx={{
          fontSize: "0.6875rem",
          color: "text.secondary",
          mt: 1,
        }}
      >
        {repo.topic}
      </Typography>
    </Box>
  );
}

export interface RepoSectionProps {
  repos: RepoRecommendation[];
}

export function RepoSection({ repos }: RepoSectionProps) {
  if (repos.length === 0) return null;

  return (
    <Box sx={{ mt: 5 }}>
      <Typography
        sx={{
          fontSize: "0.75rem",
          fontWeight: 600,
          textTransform: "uppercase",
          letterSpacing: "0.06em",
          color: "text.secondary",
          mb: 2,
        }}
      >
        Repos worth checking out
      </Typography>
      <Box sx={{ display: "grid", gap: 1.5, gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" } }}>
        {repos.map((r) => (
          <RepoCard key={r.repoUrl} repo={r} />
        ))}
      </Box>
    </Box>
  );
}
