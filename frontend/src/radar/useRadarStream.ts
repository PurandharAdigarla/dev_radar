import { useEffect, useRef, useState } from "react";
import { tokenStorage } from "../auth/tokenStorage";
import type {
  ThemeCompleteEvent,
  RadarCompleteEvent,
  RadarFailedEvent,
  ActionProposedEvent,
  AgentProgressEvent,
} from "../api/types";

export type StreamStatus = "idle" | "open" | "complete" | "failed";

export interface StreamedTheme {
  themeId: number;
  title: string;
  summary: string;
  itemIds: number[];
  displayOrder: number;
}

export interface ActivityEntry {
  id: number;
  agent: string;
  phase: "research" | "repo_discovery";
  queries: string[];
  results: Array<{ title: string; domain: string; url: string }>;
  timestamp: number;
}

export interface UseRadarStreamResult {
  status: StreamStatus;
  themes: StreamedTheme[];
  activities: ActivityEntry[];
  proposalIds: number[];
  completionMs: number | null;
  error: string | null;
}

let activityId = 0;

export function useRadarStream(radarId: number, enabled: boolean): UseRadarStreamResult {
  const [status, setStatus] = useState<StreamStatus>("idle");
  const [themes, setThemes] = useState<StreamedTheme[]>([]);
  const [activities, setActivities] = useState<ActivityEntry[]>([]);
  const [proposalIds, setProposalIds] = useState<number[]>([]);
  const [completionMs, setCompletionMs] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const esRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const token = tokenStorage.getAccess() ?? "";
    const es = new EventSource(
      `/api/radars/${radarId}/stream?token=${encodeURIComponent(token)}`,
    );
    esRef.current = es;

    const handleOpen = () => setStatus("open");
    const handleProgress = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as AgentProgressEvent;
      setActivities((prev) => [
        ...prev,
        {
          id: ++activityId,
          agent: payload.agent,
          phase: payload.phase,
          queries: payload.searchQueries,
          results: payload.searchResults,
          timestamp: Date.now(),
        },
      ]);
    };
    const handleTheme = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as ThemeCompleteEvent;
      setThemes((prev) => [
        ...prev,
        {
          themeId: payload.themeId,
          title: payload.title,
          summary: payload.summary,
          itemIds: payload.itemIds,
          displayOrder: payload.displayOrder,
        },
      ]);
    };
    const handleProposal = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as ActionProposedEvent;
      setProposalIds((prev) => (prev.includes(payload.proposalId) ? prev : [...prev, payload.proposalId]));
    };
    const handleComplete = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as RadarCompleteEvent;
      setCompletionMs(payload.generationMs);
      setStatus("complete");
      es.close();
    };
    const handleFailed = (ev: MessageEvent<string>) => {
      const payload = JSON.parse(ev.data) as RadarFailedEvent;
      setError(payload.message);
      setStatus("failed");
      es.close();
    };
    const handleError = () => {
      if (es.readyState === EventSource.CLOSED) {
        setStatus((cur) => (cur === "complete" || cur === "failed" ? cur : "failed"));
        setError((cur) => cur ?? "Connection lost");
      }
    };

    es.onopen = handleOpen;
    es.addEventListener("agent.progress", handleProgress as EventListener);
    es.addEventListener("theme.complete", handleTheme as EventListener);
    es.addEventListener("action.proposed", handleProposal as EventListener);
    es.addEventListener("radar.complete", handleComplete as EventListener);
    es.addEventListener("radar.failed", handleFailed as EventListener);
    es.onerror = handleError;

    return () => {
      es.close();
      esRef.current = null;
    };
  }, [radarId, enabled]);

  return { status, themes, activities, proposalIds, completionMs, error };
}
