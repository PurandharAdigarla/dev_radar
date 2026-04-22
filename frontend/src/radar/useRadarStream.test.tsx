import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useRadarStream } from "./useRadarStream";
import { installMockEventSource, MockEventSource } from "../test/eventSourceMock";
import { tokenStorage } from "../auth/tokenStorage";

describe("useRadarStream", () => {
  beforeEach(() => {
    installMockEventSource();
    tokenStorage.clear();
    tokenStorage.setAccess("jwt-for-stream");
  });

  afterEach(() => {
    MockEventSource.reset();
  });

  it("does not open when disabled", () => {
    renderHook(() => useRadarStream(42, false));
    expect(MockEventSource.instances).toHaveLength(0);
  });

  it("opens with ?token= query param and reports open status", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.url).toContain("?token=jwt-for-stream"));
    await waitFor(() => expect(result.current.status).toBe("open"));
  });

  it("appends themes on theme.complete events", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));

    act(() => {
      MockEventSource.last()!.__emit("theme.complete", {
        radarId: 42, themeId: 1, title: "Theme A", summary: "Summary A",
        itemIds: [1, 2], displayOrder: 0,
      });
    });
    await waitFor(() => expect(result.current.themes).toHaveLength(1));
    expect(result.current.themes[0].title).toBe("Theme A");

    act(() => {
      MockEventSource.last()!.__emit("theme.complete", {
        radarId: 42, themeId: 2, title: "Theme B", summary: "Summary B",
        itemIds: [3], displayOrder: 1,
      });
    });
    await waitFor(() => expect(result.current.themes).toHaveLength(2));
  });

  it("appends proposals on action.proposed events", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));

    act(() => {
      MockEventSource.last()!.__emit("action.proposed", {
        radarId: 42, proposalId: 7, kind: "CVE_FIX_PR", payloadJson: "{}",
      });
    });
    await waitFor(() => expect(result.current.proposalIds).toEqual([7]));
  });

  it("flips status to complete and captures elapsedMs on radar.complete", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));
    act(() => {
      MockEventSource.last()!.__emit("radar.complete", {
        radarId: 42, elapsedMs: 12500, totalTokens: 4200,
      });
    });
    await waitFor(() => expect(result.current.status).toBe("complete"));
    expect(result.current.completionMs).toBe(12500);
    expect(MockEventSource.last()!.closed).toBe(true);
  });

  it("flips status to failed and captures error on radar.failed", async () => {
    const { result } = renderHook(() => useRadarStream(42, true));
    await waitFor(() => expect(MockEventSource.last()?.readyState).toBe(1));
    act(() => {
      MockEventSource.last()!.__emit("radar.failed", {
        radarId: 42, errorCode: "GENERATION_FAILED", errorMessage: "Timed out",
      });
    });
    await waitFor(() => expect(result.current.status).toBe("failed"));
    expect(result.current.error).toBe("Timed out");
  });
});
