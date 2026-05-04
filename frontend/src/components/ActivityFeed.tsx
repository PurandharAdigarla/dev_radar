import { useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Collapse from "@mui/material/Collapse";
import { keyframes } from "@mui/system";
import type { ActivityEntry } from "../radar/useRadarStream";

const fadeIn = keyframes`
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
`;

function SearchIcon() {
  return (
    <Box
      component="svg"
      viewBox="0 0 16 16"
      fill="currentColor"
      sx={{ width: 14, height: 14, flexShrink: 0 }}
    >
      <circle cx="7" cy="7" r="5.5" fill="none" stroke="currentColor" strokeWidth="1.5" />
      <line x1="11" y1="11" x2="14" y2="14" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </Box>
  );
}

function LinkIcon() {
  return (
    <Box
      component="svg"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      sx={{ width: 13, height: 13, flexShrink: 0, mt: "1px" }}
    >
      <path d="M7 9l2-2" />
      <path d="M5.5 10.5a2.5 2.5 0 010-3.5L7 5.5" />
      <path d="M10.5 5.5a2.5 2.5 0 010 3.5L9 10.5" />
    </Box>
  );
}

function ActivityGroup({ entry, defaultOpen }: { entry: ActivityEntry; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <Box sx={{ animation: `${fadeIn} 0.3s ease-out`, mb: 1.5 }}>
      <Box
        onClick={() => setOpen(!open)}
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1,
          cursor: "pointer",
          userSelect: "none",
          py: 0.5,
          "&:hover": { opacity: 0.7 },
        }}
      >
        <SearchIcon />
        <Typography
          sx={{
            fontSize: "0.8125rem",
            fontWeight: 500,
            color: "text.secondary",
            flex: 1,
          }}
        >
          {entry.queries[0] || "Searching..."}
        </Typography>
        {entry.results.length > 0 && (
          <Typography sx={{ fontSize: "0.75rem", color: "text.secondary" }}>
            {entry.results.length} result{entry.results.length !== 1 ? "s" : ""}
          </Typography>
        )}
        <Box
          component="span"
          sx={{
            fontSize: "0.75rem",
            color: "text.secondary",
            transform: open ? "rotate(180deg)" : "rotate(0deg)",
            transition: "transform 0.2s",
          }}
        >
          ▾
        </Box>
      </Box>

      <Collapse in={open}>
        <Box
          sx={{
            ml: "6px",
            pl: 2,
            borderLeft: "1px solid",
            borderColor: "divider",
            mt: 0.5,
          }}
        >
          {entry.queries.slice(1).map((q, i) => (
            <Box key={i} sx={{ display: "flex", alignItems: "center", gap: 1, py: 0.25 }}>
              <SearchIcon />
              <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
                {q}
              </Typography>
            </Box>
          ))}
          {entry.results.map((r, i) => (
            <Box
              key={i}
              sx={{
                display: "flex",
                alignItems: "flex-start",
                gap: 1,
                py: 0.5,
                px: 1.5,
                my: 0.25,
                borderRadius: 1,
                bgcolor: "rgba(45,42,38,0.02)",
              }}
            >
              <LinkIcon />
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography
                  sx={{
                    fontSize: "0.8125rem",
                    color: "text.primary",
                    lineHeight: 1.4,
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    whiteSpace: "nowrap",
                  }}
                >
                  {r.title || r.domain}
                </Typography>
                {r.domain && r.title && (
                  <Typography sx={{ fontSize: "0.6875rem", color: "text.secondary" }}>
                    {r.domain}
                  </Typography>
                )}
              </Box>
            </Box>
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}

export interface ActivityFeedProps {
  activities: ActivityEntry[];
  streaming: boolean;
}

export function ActivityFeed({ activities, streaming }: ActivityFeedProps) {
  if (activities.length === 0 && !streaming) return null;

  const researchActivities = activities.filter((a) => a.phase === "research");
  const repoActivities = activities.filter((a) => a.phase === "repo_discovery");

  const hasResearch = researchActivities.length > 0;
  const hasRepos = repoActivities.length > 0;

  return (
    <Box
      sx={{
        mb: 4,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: 2,
        bgcolor: "background.paper",
        overflow: "hidden",
      }}
    >
      {hasResearch && (
        <Box sx={{ px: 2.5, pt: 2, pb: 1 }}>
          <Typography
            sx={{
              fontSize: "0.75rem",
              fontWeight: 600,
              textTransform: "uppercase",
              letterSpacing: "0.06em",
              color: "text.secondary",
              mb: 1,
            }}
          >
            Researching topics
          </Typography>
          {researchActivities.map((a) => (
            <ActivityGroup
              key={a.id}
              entry={a}
              defaultOpen={a === researchActivities[researchActivities.length - 1]}
            />
          ))}
        </Box>
      )}

      {hasRepos && (
        <Box
          sx={{
            px: 2.5,
            pt: hasResearch ? 1.5 : 2,
            pb: 1,
            borderTop: hasResearch ? "1px solid" : "none",
            borderColor: "divider",
          }}
        >
          <Typography
            sx={{
              fontSize: "0.75rem",
              fontWeight: 600,
              textTransform: "uppercase",
              letterSpacing: "0.06em",
              color: "text.secondary",
              mb: 1,
            }}
          >
            Discovering repos
          </Typography>
          {repoActivities.map((a) => (
            <ActivityGroup
              key={a.id}
              entry={a}
              defaultOpen={a === repoActivities[repoActivities.length - 1]}
            />
          ))}
        </Box>
      )}

      {streaming && activities.length === 0 && (
        <Box sx={{ px: 2.5, py: 2.5 }}>
          <Typography sx={{ fontSize: "0.8125rem", color: "text.secondary" }}>
            Starting AI agents...
          </Typography>
        </Box>
      )}
    </Box>
  );
}
