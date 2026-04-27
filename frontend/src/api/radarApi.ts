import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { PageResponse, PublicWeeklyRadar, RadarDetail, RadarSummary } from "./types";

export interface ListRadarsArgs {
  page?: number;
  size?: number;
}

export interface PublicWeeklyRadarArgs {
  tagSlug: string;
  weekNumber: number;
}

export interface ShareRadarResponse {
  shareToken: string;
  shareUrl: string;
}

export const radarApi = createApi({
  reducerPath: "radarApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Radar", "RadarList"],
  endpoints: (b) => ({
    list: b.query<PageResponse<RadarSummary>, ListRadarsArgs>({
      query: ({ page = 0, size = 20 } = {}) => ({
        url: "/api/radars",
        params: { page, size, sort: "generatedAt,desc" },
      }),
      providesTags: ["RadarList"],
    }),
    get: b.query<RadarDetail, number>({
      query: (id) => ({ url: `/api/radars/${id}` }),
      providesTags: (_r, _e, id) => [{ type: "Radar", id }],
    }),
    create: b.mutation<RadarSummary, void>({
      query: () => ({ url: "/api/radars", method: "POST" }),
      invalidatesTags: ["RadarList"],
    }),
    shareRadar: b.mutation<ShareRadarResponse, number>({
      query: (id) => ({ url: `/api/radars/${id}/share`, method: "POST" }),
    }),
    getSharedRadar: b.query<RadarDetail, string>({
      query: (shareToken) => ({ url: `/api/radars/shared/${shareToken}` }),
    }),
    getSampleRadar: b.query<RadarDetail, void>({
      query: () => ({ url: "/api/sample-radar" }),
    }),
    getPublicWeeklyRadar: b.query<PublicWeeklyRadar, PublicWeeklyRadarArgs>({
      query: ({ tagSlug, weekNumber }) => ({
        url: `/api/public/radar/${tagSlug}/week/${weekNumber}`,
      }),
    }),
  }),
});

export const {
  useListQuery: useListRadarsQuery,
  useGetQuery: useGetRadarQuery,
  useCreateMutation: useCreateRadarMutation,
  useShareRadarMutation,
  useGetSharedRadarQuery,
  useGetSampleRadarQuery,
  useGetPublicWeeklyRadarQuery,
} = radarApi;
