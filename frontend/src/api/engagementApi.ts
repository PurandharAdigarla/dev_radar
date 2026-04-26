import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";

export type EventType = "THUMBS_UP" | "THUMBS_DOWN" | "SHARE" | "CLICK";

export interface EngagementEventRequest {
  radarId: number;
  themeIndex: number;
  eventType: EventType;
}

export interface EngagementSummary {
  thumbsUpThemes: string[];
  thumbsDownThemes: string[];
  totalInteractions: number;
  topEventTypes: Record<string, number>;
}

export interface RadarEngagement {
  themeIndex: number;
  eventType: "THUMBS_UP" | "THUMBS_DOWN";
}

export const engagementApi = createApi({
  reducerPath: "engagementApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["EngagementSummary"],
  endpoints: (b) => ({
    postEngagement: b.mutation<void, EngagementEventRequest>({
      query: (body) => ({
        url: "/api/engagement",
        method: "POST",
        body,
      }),
      invalidatesTags: ["EngagementSummary"],
    }),
    getEngagementSummary: b.query<EngagementSummary, void>({
      query: () => ({ url: "/api/engagement/summary" }),
      providesTags: ["EngagementSummary"],
    }),
    getRadarEngagements: b.query<RadarEngagement[], number>({
      query: (radarId) => ({ url: `/api/engagement/radar/${radarId}` }),
      providesTags: (_r, _e, radarId) => [{ type: "EngagementSummary", id: radarId }],
    }),
  }),
});

export const {
  usePostEngagementMutation,
  useGetEngagementSummaryQuery,
  useGetRadarEngagementsQuery,
} = engagementApi;
