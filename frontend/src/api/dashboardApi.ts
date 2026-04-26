import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";

export interface UserStats {
  radarCount: number;
  themeCount: number;
  engagementCount: number;
  latestRadarDate: string | null;
  newItemsSinceLastRadar: number;
}

export interface DependencySummary {
  repoCount: number;
  dependencyCount: number;
  vulnerabilityCount: number;
  ecosystems: string[];
}

export const dashboardApi = createApi({
  reducerPath: "dashboardApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["UserStats", "DependencySummary"],
  endpoints: (b) => ({
    getUserStats: b.query<UserStats, void>({
      query: () => ({ url: "/api/users/me/stats" }),
      providesTags: ["UserStats"],
    }),
    getDependencySummary: b.query<DependencySummary, void>({
      query: () => ({ url: "/api/users/me/dependency-summary" }),
      providesTags: ["DependencySummary"],
    }),
    getSuggestedInterests: b.query<string[], void>({
      query: () => ({ url: "/api/users/me/suggested-interests" }),
    }),
  }),
});

export const {
  useGetUserStatsQuery,
  useGetDependencySummaryQuery,
  useGetSuggestedInterestsQuery,
} = dashboardApi;
