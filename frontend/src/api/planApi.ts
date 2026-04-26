import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";

export interface PlanInfo {
  plan: "FREE" | "PRO" | "TEAM";
  radarsPerMonth: number;
  sourcesPerRadar: number;
  maxInterests: number;
  teamAccess: boolean;
  emailDigest: boolean;
  apiKeyAccess: boolean;
  autoFixPrs: boolean;
  trialActive: boolean;
  trialUsed: boolean;
  planExpiresAt: string | null;
}

export const planApi = createApi({
  reducerPath: "planApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Plan"],
  endpoints: (b) => ({
    getPlan: b.query<PlanInfo, void>({
      query: () => ({ url: "/api/plan" }),
      providesTags: ["Plan"],
    }),
    startTrial: b.mutation<PlanInfo, void>({
      query: () => ({ url: "/api/plan/start-trial", method: "POST" }),
      invalidatesTags: ["Plan"],
    }),
  }),
});

export const {
  useGetPlanQuery,
  useStartTrialMutation,
} = planApi;
