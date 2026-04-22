import { describe, it, expect } from "vitest";
import {
  radarGenerationReducer,
  generationStarted,
  generationFinished,
} from "./radarGenerationSlice";

const initial = radarGenerationReducer(undefined, { type: "@@INIT" });

describe("radarGenerationSlice", () => {
  it("starts with no generating radar", () => {
    expect(initial.currentGeneratingRadarId).toBeNull();
    expect(initial.startedAt).toBeNull();
  });

  it("tracks a radar when generation starts", () => {
    const s = radarGenerationReducer(
      initial,
      generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }),
    );
    expect(s.currentGeneratingRadarId).toBe(100);
    expect(s.startedAt).toBe("2026-04-22T10:00:00Z");
  });

  it("clears on generationFinished", () => {
    const running = radarGenerationReducer(
      initial,
      generationStarted({ radarId: 100, startedAt: "2026-04-22T10:00:00Z" }),
    );
    const done = radarGenerationReducer(running, generationFinished());
    expect(done.currentGeneratingRadarId).toBeNull();
    expect(done.startedAt).toBeNull();
  });
});
