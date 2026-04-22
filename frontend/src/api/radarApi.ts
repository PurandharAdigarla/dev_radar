import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { PageResponse, RadarDetail, RadarSummary } from "./types";

export interface ListRadarsArgs {
  page?: number;
  size?: number;
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
  }),
});

export const {
  useListQuery: useListRadarsQuery,
  useGetQuery: useGetRadarQuery,
  useCreateMutation: useCreateRadarMutation,
} = radarApi;
