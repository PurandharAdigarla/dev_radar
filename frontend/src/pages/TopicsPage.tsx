import { useState, useCallback } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import IconButton from "@mui/material/IconButton";
import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import { motion, AnimatePresence } from "framer-motion";
import { Button } from "../components/Button";
import { Alert } from "../components/Alert";
import { colors, fonts } from "../theme";
import { useGetTopicsQuery, useSetTopicsMutation } from "../api/topicApi";

const MAX_TOPICS = 15;

const SUGGESTIONS = [
  "MCP Servers",
  "Claude Code Skills",
  "AI Agent Frameworks",
  "LLM Prompt Engineering",
  "RAG Pipelines",
  "Agentic Coding",
  "Tool Use Patterns",
  "Multi-Agent Systems",
  "AI Code Review",
  "Vector Databases",
  "LLM Fine-tuning",
  "AI Testing",
];

/* ── animation variants ───────────────────────────────────────── */

const chipVariants = {
  initial: { opacity: 0, scale: 0.8 },
  animate: { opacity: 1, scale: 1, transition: { duration: 0.2, ease: [0.4, 0, 0.2, 1] as const } },
  exit: { opacity: 0, scale: 0.8, transition: { duration: 0.15 } },
};

export function TopicsPage() {
  const { data: topics = [], isLoading } = useGetTopicsQuery();
  const [setTopics, { isLoading: isSaving }] = useSetTopicsMutation();
  const [draft, setDraft] = useState("");
  const [localTopics, setLocalTopics] = useState<string[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const current = localTopics ?? topics.map((t) => t.topic);

  const addTopic = useCallback(
    (name: string) => {
      const trimmed = name.trim();
      if (!trimmed) return;
      if (current.length >= MAX_TOPICS) {
        setError(`Maximum ${MAX_TOPICS} topics allowed.`);
        return;
      }
      if (current.some((t) => t.toLowerCase() === trimmed.toLowerCase())) {
        setError("Topic already added.");
        return;
      }
      setError(null);
      setLocalTopics([...current, trimmed]);
      setDraft("");
    },
    [current],
  );

  const removeTopic = useCallback(
    (index: number) => {
      setLocalTopics(current.filter((_, i) => i !== index));
      setError(null);
    },
    [current],
  );

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      addTopic(draft);
    }
  };

  const handleSave = async () => {
    if (current.length === 0) {
      setError("Add at least one topic.");
      return;
    }
    try {
      await setTopics({ topics: current }).unwrap();
      setLocalTopics(null);
      setError(null);
    } catch {
      setError("Failed to save topics. Try again.");
    }
  };

  const hasChanges =
    localTopics !== null &&
    JSON.stringify(localTopics) !== JSON.stringify(topics.map((t) => t.topic));

  const unusedSuggestions = SUGGESTIONS.filter(
    (s) => !current.some((c) => c.toLowerCase() === s.toLowerCase()),
  );

  if (isLoading) {
    return (
      <Box sx={{ maxWidth: 720 }}>
        <Typography variant="body2" color="text.secondary">
          Loading topics...
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 720, width: "100%" }}>
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "flex-end",
            justifyContent: "space-between",
            gap: 3,
            mb: 4,
            flexWrap: "wrap",
          }}
        >
          <Box>
            <Typography
              component="h1"
              sx={{
                m: 0,
                fontFamily: fonts.headline,
                fontSize: "2rem",
                fontWeight: 700,
                letterSpacing: "-0.03em",
                color: colors.text,
                position: "relative",
                display: "inline-block",
                "&::after": {
                  content: '""',
                  position: "absolute",
                  bottom: -4,
                  left: 0,
                  width: "100%",
                  height: 3,
                  background: colors.gradientPrimary,
                  borderRadius: 2,
                },
              }}
            >
              Your Topics
            </Typography>
            <Typography
              sx={{
                mt: 2,
                fontSize: "0.875rem",
                fontWeight: 500,
                color: colors.textSecondary,
              }}
            >
              {current.length} of {MAX_TOPICS}
            </Typography>
          </Box>

          <AnimatePresence>
            {hasChanges && (
              <motion.div
                initial={{ opacity: 0, y: 8, scale: 0.95 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 8, scale: 0.95 }}
                transition={{ duration: 0.2 }}
              >
                <Button onClick={handleSave} disabled={isSaving}>
                  {isSaving ? "Saving..." : "Save Changes"}
                </Button>
              </motion.div>
            )}
          </AnimatePresence>
        </Box>
      </motion.div>

      {error && (
        <Box sx={{ mb: 3 }}>
          <Alert severity="error">{error}</Alert>
        </Box>
      )}

      {/* Add input */}
      <Box sx={{ display: "flex", gap: 1, mb: 3 }}>
        <TextField
          fullWidth
          size="small"
          placeholder="Type a topic and press Enter (e.g. 'MCP Servers')"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={current.length >= MAX_TOPICS}
          sx={{
            "& .MuiOutlinedInput-root": {
              borderRadius: "10px",
              "&.Mui-focused": {
                boxShadow: `0 0 0 3px rgba(87,83,78,0.15)`,
              },
            },
          }}
        />
        <IconButton
          onClick={() => addTopic(draft)}
          disabled={!draft.trim() || current.length >= MAX_TOPICS}
          sx={{
            width: 42,
            height: 42,
            borderRadius: "10px",
            background: colors.gradientPrimary,
            color: "#fff",
            transition: "all 0.2s ease",
            "&:hover": {
              filter: "brightness(1.1)",
              transform: "translateY(-1px)",
              boxShadow: colors.glowPrimary,
            },
            "&:disabled": {
              background: colors.divider,
              color: colors.textMuted,
            },
          }}
        >
          <AddIcon />
        </IconButton>
      </Box>

      {/* Topic chips */}
      {current.length > 0 && (
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mb: 4 }}>
          <AnimatePresence mode="popLayout">
            {current.map((topic, i) => (
              <motion.div
                key={`${topic}-${i}`}
                variants={chipVariants}
                initial="initial"
                animate="animate"
                exit="exit"
                layout
              >
                <Box
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 0.75,
                    px: 1.75,
                    py: 0.875,
                    borderRadius: 999,
                    bgcolor: "rgba(87,83,78,0.1)",
                    color: colors.primary,
                    border: "1px solid rgba(87,83,78,0.2)",
                    fontSize: "0.875rem",
                    fontWeight: 500,
                    transition: "all 0.15s ease",
                    "&:hover": {
                      bgcolor: "rgba(87,83,78,0.15)",
                      "& .chip-close": { opacity: 1 },
                    },
                  }}
                >
                  {topic}
                  <Box
                    component="button"
                    className="chip-close"
                    onClick={() => removeTopic(i)}
                    sx={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      width: 18,
                      height: 18,
                      borderRadius: "50%",
                      border: "none",
                      padding: 0,
                      bgcolor: "rgba(87,83,78,0.15)",
                      color: colors.primary,
                      cursor: "pointer",
                      opacity: 0.6,
                      transition: "all 0.15s ease",
                      "&:hover": {
                        bgcolor: "rgba(87,83,78,0.3)",
                        opacity: 1,
                      },
                    }}
                  >
                    <CloseIcon sx={{ fontSize: 14 }} />
                  </Box>
                </Box>
              </motion.div>
            ))}
          </AnimatePresence>
        </Box>
      )}

      {/* Suggestions */}
      {unusedSuggestions.length > 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.4, delay: 0.2 }}
        >
          <Box sx={{ mb: 4 }}>
            <Typography
              sx={{
                fontSize: "0.8125rem",
                fontWeight: 600,
                color: colors.textMuted,
                textTransform: "uppercase",
                letterSpacing: "0.06em",
                mb: 1.5,
              }}
            >
              Suggestions
            </Typography>
            <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
              {unusedSuggestions.map((s) => (
                <Box
                  key={s}
                  component="button"
                  onClick={() => addTopic(s)}
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    px: 1.5,
                    py: 0.75,
                    borderRadius: 999,
                    border: `1px solid ${colors.divider}`,
                    bgcolor: "transparent",
                    color: colors.textSecondary,
                    fontSize: "0.8125rem",
                    fontWeight: 500,
                    fontFamily: "inherit",
                    cursor: "pointer",
                    transition: "all 0.15s ease",
                    "&:hover": {
                      bgcolor: "rgba(87,83,78,0.08)",
                      borderColor: "rgba(87,83,78,0.3)",
                      color: colors.primary,
                    },
                  }}
                >
                  + {s}
                </Box>
              ))}
            </Box>
          </Box>
        </motion.div>
      )}
    </Box>
  );
}
