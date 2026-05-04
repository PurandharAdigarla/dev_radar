import { useState, useEffect, useRef } from "react";
import { Link as RouterLink } from "react-router-dom";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import TagRounded from "@mui/icons-material/TagRounded";
import PsychologyRounded from "@mui/icons-material/PsychologyRounded";
import RadarRounded from "@mui/icons-material/RadarRounded";
import { motion, useInView } from "framer-motion";
import { Button } from "../components/Button";
import { ThemeCard } from "../components/ThemeCard";
import { useGetSampleRadarQuery } from "../api/radarApi";
import { colors, fonts } from "../theme";
import type { RadarTheme } from "../api/types";

/* ─── Fallback data ─── */

const FALLBACK_THEMES: RadarTheme[] = [
  {
    id: -1,
    displayOrder: 0,
    title: "Claude Code Gets Multi-Agent Orchestration",
    summary:
      "Anthropic released Claude Code 1.0 with native multi-agent support. " +
      "You can now spawn sub-agents for parallel tasks like research, code review, " +
      "and testing. The new Agent SDK makes it trivial to build custom workflows " +
      "that combine Claude's reasoning with tool use.",
    items: [],
  },
  {
    id: -2,
    displayOrder: 1,
    title: "MCP Protocol Reaches 1.0 — Ecosystem Explodes",
    summary:
      "The Model Context Protocol hit v1.0 with 200+ community-built servers. " +
      "Key additions: streaming tool results, OAuth for remote servers, and " +
      "a standard discovery mechanism. If you build AI tools, MCP is now the " +
      "interoperability layer to target.",
    items: [],
  },
  {
    id: -3,
    displayOrder: 2,
    title: "Google ADK for Java Enables Agentic Pipelines",
    summary:
      "Google's Agent Development Kit shipped Java support with ParallelAgent, " +
      "SequentialAgent, and built-in Google Search grounding. Teams building " +
      "multi-step AI workflows can now use type-safe Java instead of Python.",
    items: [],
  },
];

/* ─── Animation variants ─── */

const fadeUp = {
  hidden: { opacity: 0, y: 30 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { delay: i * 0.12, duration: 0.6, ease: [0.22, 1, 0.36, 1] as const },
  }),
};

const staggerContainer = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.15 } },
};

const cardReveal = {
  hidden: { opacity: 0, y: 40 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] as const } },
};

/* ─── Gradient text helper ─── */

const gradientTextSx = {
  background: colors.gradientText,
  backgroundClip: "text",
  WebkitBackgroundClip: "text",
  WebkitTextFillColor: "transparent",
} as const;

/* ─── Animated blobs keyframes (injected once) ─── */

const blobKeyframes = `
@keyframes blob1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(30px, -50px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
}
@keyframes blob2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-40px, 30px) scale(0.9); }
  66% { transform: translate(30px, -20px) scale(1.1); }
}
@keyframes blob3 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(20px, 40px) scale(1.05); }
  66% { transform: translate(-30px, -30px) scale(0.95); }
}
`;

/* ─── Navbar ─── */

function Navbar() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    function onScroll() {
      setScrolled(window.scrollY > 20);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <Box
      component="nav"
      sx={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        zIndex: 100,
        px: { xs: 2, md: 4 },
        py: 1.5,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        transition: "all 0.3s ease",
        backdropFilter: scrolled ? "blur(12px)" : "none",
        backgroundColor: scrolled ? "rgba(28,25,23,0.9)" : "transparent",
        borderBottom: scrolled ? "1px solid rgba(255,255,255,0.06)" : "1px solid transparent",
      }}
    >
      <Typography
        sx={{
          fontFamily: fonts.headline,
          fontWeight: 700,
          fontSize: "1.125rem",
          color: "#fff",
          letterSpacing: "-0.02em",
        }}
      >
        Dev Radar
      </Typography>
      <Box sx={{ display: "flex", gap: 1.5, alignItems: "center" }}>
        <Button
          component={RouterLink}
          to="/login"
          variant="text"
          sx={{
            color: "rgba(255,255,255,0.7)",
            "&:hover": { color: "#fff", backgroundColor: "rgba(255,255,255,0.06)" },
          }}
        >
          Sign In
        </Button>
        <Button component={RouterLink} to="/register">
          Get Started
        </Button>
      </Box>
    </Box>
  );
}

/* ─── Hero floating preview card ─── */

function FloatingPreviewCard() {
  return (
    <Box
      sx={{
        position: "relative",
        perspective: "1200px",
        mt: { xs: 6, md: 0 },
        ml: { md: 4 },
        flexShrink: 0,
      }}
    >
      <motion.div
        initial={{ opacity: 0, rotateY: 12, rotateX: -4, x: 40 }}
        animate={{ opacity: 1, rotateY: 6, rotateX: -2, x: 0 }}
        transition={{ duration: 1, delay: 0.4, ease: [0.22, 1, 0.36, 1] as const }}
      >
        <Box
          sx={{
            width: { xs: 320, md: 360 },
            bgcolor: "#fff",
            borderRadius: 3,
            p: 3,
            boxShadow: `0 20px 60px rgba(0,0,0,0.3), ${colors.glowPrimary}`,
            transform: "rotateY(6deg) rotateX(-2deg)",
            transformStyle: "preserve-3d",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1,
              mb: 2,
            }}
          >
            <Box
              sx={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                background: colors.gradientPrimary,
              }}
            />
            <Typography
              sx={{
                fontFamily: fonts.headline,
                fontWeight: 600,
                fontSize: "0.6875rem",
                textTransform: "uppercase",
                letterSpacing: "0.1em",
                color: colors.textMuted,
              }}
            >
              Daily Radar
            </Typography>
          </Box>
          <Typography
            sx={{
              fontFamily: fonts.headline,
              fontWeight: 600,
              fontSize: "1rem",
              color: colors.text,
              mb: 1,
              lineHeight: 1.3,
            }}
          >
            Claude Code Gets Multi-Agent Orchestration
          </Typography>
          <Typography sx={{ fontSize: "0.8125rem", color: colors.textSecondary, lineHeight: 1.6 }}>
            Anthropic released Claude Code 1.0 with native multi-agent support.
            Spawn sub-agents for parallel research, review, and testing.
          </Typography>
          <Box sx={{ display: "flex", gap: 1, mt: 2 }}>
            {["AI Agents", "Claude", "MCP"].map((tag) => (
              <Box
                key={tag}
                sx={{
                  fontSize: "0.6875rem",
                  fontWeight: 600,
                  px: 1.5,
                  py: 0.5,
                  borderRadius: 1,
                  background: `linear-gradient(135deg, rgba(87,83,78,0.1), rgba(120,113,108,0.1))`,
                  color: colors.primary,
                }}
              >
                {tag}
              </Box>
            ))}
          </Box>
        </Box>
      </motion.div>
    </Box>
  );
}

/* ─── How It Works card ─── */

const howItWorksData = [
  {
    icon: TagRounded,
    number: "01",
    title: "Pick your topics",
    description:
      "Choose the AI domains you care about — MCP servers, agent frameworks, prompt engineering, and more.",
  },
  {
    icon: PsychologyRounded,
    number: "02",
    title: "AI agents research",
    description:
      "Parallel AI agents search the web, find new tools, repos, and developments for each of your topics.",
  },
  {
    icon: RadarRounded,
    number: "03",
    title: "Get your radar",
    description:
      "Receive a curated brief with themes, source citations, and repo recommendations you can act on.",
  },
];

function HowItWorksSection() {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });

  return (
    <Box
      ref={ref}
      sx={{
        py: { xs: 10, md: 14 },
        bgcolor: colors.bgDefault,
      }}
    >
      <Container maxWidth="lg" sx={{ px: { xs: 3, md: 4 } }}>
        <motion.div
          initial="hidden"
          animate={isInView ? "visible" : "hidden"}
          variants={staggerContainer}
        >
          <motion.div variants={cardReveal}>
            <Typography
              variant="h2"
              sx={{
                textAlign: "center",
                mb: 1.5,
                fontFamily: fonts.headline,
              }}
            >
              How It{" "}
              <Box component="span" sx={gradientTextSx}>
                Works
              </Box>
            </Typography>
            <Typography
              variant="body1"
              sx={{
                textAlign: "center",
                color: colors.textSecondary,
                mb: 8,
                maxWidth: 480,
                mx: "auto",
              }}
            >
              Three simple steps to stay ahead of the curve.
            </Typography>
          </motion.div>

          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" },
              gap: 3,
            }}
          >
            {howItWorksData.map((step) => {
              const Icon = step.icon;
              return (
                <motion.div key={step.number} variants={cardReveal}>
                  <Box
                    sx={{
                      p: 4,
                      borderRadius: 3,
                      border: `1px solid ${colors.divider}`,
                      bgcolor: colors.bgPaper,
                      height: "100%",
                      position: "relative",
                      overflow: "hidden",
                      transition: "all 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
                      "&:hover": {
                        transform: "translateY(-4px)",
                        borderColor: "rgba(87,83,78,0.3)",
                        boxShadow: `0 12px 40px rgba(87,83,78,0.1)`,
                      },
                    }}
                  >
                    <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2.5 }}>
                      <Box
                        sx={{
                          width: 44,
                          height: 44,
                          borderRadius: 2.5,
                          background: colors.gradientPrimary,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          flexShrink: 0,
                        }}
                      >
                        <Icon sx={{ color: "#fff", fontSize: 22 }} />
                      </Box>
                      <Typography
                        sx={{
                          fontFamily: fonts.headline,
                          fontWeight: 700,
                          fontSize: "0.8125rem",
                          ...gradientTextSx,
                        }}
                      >
                        {step.number}
                      </Typography>
                    </Box>
                    <Typography
                      sx={{
                        fontFamily: fonts.headline,
                        fontWeight: 600,
                        fontSize: "1.125rem",
                        color: colors.text,
                        mb: 1,
                      }}
                    >
                      {step.title}
                    </Typography>
                    <Typography sx={{ fontSize: "0.875rem", color: colors.textSecondary, lineHeight: 1.7 }}>
                      {step.description}
                    </Typography>
                  </Box>
                </motion.div>
              );
            })}
          </Box>
        </motion.div>
      </Container>
    </Box>
  );
}

/* ─── Sample Radar section ─── */

function SampleRadarSection() {
  const ref = useRef<HTMLDivElement>(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });
  const { data: sampleRadar, isLoading, isError } = useGetSampleRadarQuery();

  const themes = isError || !sampleRadar ? FALLBACK_THEMES : sampleRadar.themes;

  return (
    <Box ref={ref} sx={{ py: { xs: 10, md: 14 }, bgcolor: colors.bgPaper }}>
      <Container maxWidth="md" sx={{ px: { xs: 3, md: 4 } }}>
        <motion.div
          initial="hidden"
          animate={isInView ? "visible" : "hidden"}
          variants={staggerContainer}
        >
          <motion.div variants={cardReveal}>
            <Typography
              variant="h2"
              sx={{ textAlign: "center", mb: 1.5, fontFamily: fonts.headline }}
            >
              A{" "}
              <Box component="span" sx={gradientTextSx}>
                Sample
              </Box>{" "}
              Radar
            </Typography>
            <Typography
              variant="body1"
              sx={{
                textAlign: "center",
                color: colors.textSecondary,
                mb: 6,
                maxWidth: 480,
                mx: "auto",
              }}
            >
              Here is what a daily brief looks like.
            </Typography>
          </motion.div>

          <motion.div variants={cardReveal}>
            {/* Browser window mockup */}
            <Box
              sx={{
                borderRadius: 3,
                overflow: "hidden",
                border: `1px solid ${colors.divider}`,
                boxShadow: "0 20px 60px rgba(0,0,0,0.08)",
              }}
            >
              {/* Browser chrome */}
              <Box
                sx={{
                  bgcolor: colors.bgSubtle,
                  px: 2.5,
                  py: 1.5,
                  display: "flex",
                  alignItems: "center",
                  gap: 1,
                  borderBottom: `1px solid ${colors.divider}`,
                }}
              >
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    bgcolor: "#ef4444",
                    opacity: 0.7,
                  }}
                />
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    bgcolor: "#f59e0b",
                    opacity: 0.7,
                  }}
                />
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: "50%",
                    bgcolor: "#10b981",
                    opacity: 0.7,
                  }}
                />
                <Box
                  sx={{
                    ml: 2,
                    flex: 1,
                    bgcolor: colors.bgPaper,
                    borderRadius: 1.5,
                    px: 2,
                    py: 0.5,
                    fontSize: "0.75rem",
                    color: colors.textMuted,
                    border: `1px solid ${colors.divider}`,
                  }}
                >
                  devradar.app/radar/today
                </Box>
              </Box>

              {/* Content */}
              <Box sx={{ bgcolor: colors.bgPaper, p: { xs: 3, md: 4 } }}>
                {isLoading ? (
                  <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
                    <CircularProgress size={28} />
                  </Box>
                ) : (
                  themes.map((theme) => <ThemeCard key={theme.id} theme={theme} />)
                )}
              </Box>
            </Box>
          </motion.div>
        </motion.div>
      </Container>
    </Box>
  );
}

/* ─── Footer ─── */

function Footer() {
  return (
    <Box
      component="footer"
      sx={{
        py: 4,
        bgcolor: "#1c1917",
        borderTop: "1px solid rgba(255,255,255,0.06)",
      }}
    >
      <Container
        maxWidth="lg"
        sx={{
          px: { xs: 3, md: 4 },
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
          gap: 2,
        }}
      >
        <Typography
          sx={{
            fontFamily: fonts.headline,
            fontWeight: 700,
            fontSize: "0.875rem",
            color: "rgba(255,255,255,0.5)",
          }}
        >
          Dev Radar
        </Typography>
        <Typography sx={{ fontSize: "0.8125rem", color: "rgba(255,255,255,0.3)" }}>
          &copy; {new Date().getFullYear()} Dev Radar. All rights reserved.
        </Typography>
      </Container>
    </Box>
  );
}

/* ─── Main Landing page ─── */

export function Landing() {
  return (
    <Box sx={{ minHeight: "100vh", bgcolor: colors.bgDefault }}>
      {/* Inject blob keyframes */}
      <style>{blobKeyframes}</style>

      <Navbar />

      {/* Hero */}
      <Box
        sx={{
          position: "relative",
          overflow: "hidden",
          background: colors.gradientHero,
          pt: { xs: 14, md: 20 },
          pb: { xs: 10, md: 16 },
        }}
      >
        {/* Animated background blobs */}
        <Box
          sx={{
            position: "absolute",
            inset: 0,
            overflow: "hidden",
            pointerEvents: "none",
          }}
        >
          <Box
            sx={{
              position: "absolute",
              width: 500,
              height: 500,
              borderRadius: "50%",
              background: "radial-gradient(circle, rgba(87,83,78,0.12) 0%, transparent 70%)",
              top: "-10%",
              left: "10%",
              filter: "blur(80px)",
              animation: "blob1 20s ease-in-out infinite",
            }}
          />
          <Box
            sx={{
              position: "absolute",
              width: 400,
              height: 400,
              borderRadius: "50%",
              background: "radial-gradient(circle, rgba(120,113,108,0.1) 0%, transparent 70%)",
              top: "40%",
              right: "5%",
              filter: "blur(80px)",
              animation: "blob2 25s ease-in-out infinite",
            }}
          />
          <Box
            sx={{
              position: "absolute",
              width: 350,
              height: 350,
              borderRadius: "50%",
              background: "radial-gradient(circle, rgba(168,162,158,0.08) 0%, transparent 70%)",
              bottom: "-5%",
              left: "40%",
              filter: "blur(80px)",
              animation: "blob3 22s ease-in-out infinite",
            }}
          />
        </Box>

        <Container maxWidth="lg" sx={{ position: "relative", zIndex: 1, px: { xs: 3, md: 4 } }}>
          <Box
            sx={{
              display: "flex",
              flexDirection: { xs: "column", md: "row" },
              alignItems: { md: "center" },
              justifyContent: "space-between",
            }}
          >
            {/* Left: copy */}
            <Box sx={{ maxWidth: 600, flex: 1 }}>
              <motion.div initial="hidden" animate="visible" variants={staggerContainer}>
                <motion.div custom={0} variants={fadeUp}>
                  <Typography
                    variant="overline"
                    sx={{
                      display: "inline-block",
                      mb: 3,
                      color: "rgba(255,255,255,0.5)",
                      borderRadius: 999,
                      border: "1px solid rgba(255,255,255,0.1)",
                      px: 2,
                      py: 0.5,
                      fontSize: "0.6875rem",
                    }}
                  >
                    AI-Powered Developer Intelligence
                  </Typography>
                </motion.div>

                <motion.div custom={1} variants={fadeUp}>
                  <Typography
                    variant="h1"
                    component="h1"
                    sx={{
                      color: "#fff",
                      fontFamily: fonts.headline,
                      textWrap: "balance",
                    }}
                  >
                    Your{" "}
                    <Box component="span" sx={gradientTextSx}>
                      AI-Powered
                    </Box>{" "}
                    Developer Radar
                  </Typography>
                </motion.div>

                <motion.div custom={2} variants={fadeUp}>
                  <Typography
                    sx={{
                      color: "rgba(255,255,255,0.7)",
                      fontSize: "1.0625rem",
                      lineHeight: 1.7,
                      mt: 3,
                      mb: 5,
                      maxWidth: 520,
                      textWrap: "pretty",
                    }}
                  >
                    Dev Radar uses AI agents to research your chosen topics, find new tools,
                    MCP servers, and repos — then delivers a curated daily brief so you never
                    fall behind.
                  </Typography>
                </motion.div>

                <motion.div custom={3} variants={fadeUp}>
                  <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
                    <Button
                      component={RouterLink}
                      to="/register"
                      sx={{
                        px: 4,
                        py: 1.5,
                        fontSize: "0.9375rem",
                        boxShadow: colors.glowPrimary,
                        "&:hover": {
                          boxShadow: `0 0 30px rgba(87,83,78,0.5), 0 0 80px rgba(87,83,78,0.2)`,
                        },
                      }}
                    >
                      Start Free
                    </Button>
                    <Button
                      variant="outlined"
                      href="#sample"
                      sx={{
                        px: 4,
                        py: 1.5,
                        fontSize: "0.9375rem",
                        borderColor: "rgba(255,255,255,0.2)",
                        color: "#fff",
                        "&:hover": {
                          borderColor: "rgba(255,255,255,0.5)",
                          backgroundColor: "rgba(255,255,255,0.05)",
                          color: "#fff",
                        },
                      }}
                    >
                      See a sample
                    </Button>
                  </Box>
                </motion.div>
              </motion.div>
            </Box>

            {/* Right: floating card */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.8, delay: 0.3 }}
            >
              <Box sx={{ display: { xs: "none", md: "block" } }}>
                <FloatingPreviewCard />
              </Box>
            </motion.div>
          </Box>
        </Container>
      </Box>

      {/* How it works */}
      <HowItWorksSection />

      {/* Sample Radar */}
      <Box id="sample">
        <SampleRadarSection />
      </Box>

      {/* CTA */}

      {/* Footer */}
      <Footer />
    </Box>
  );
}
