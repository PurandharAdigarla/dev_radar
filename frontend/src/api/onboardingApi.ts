import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";

export interface RepoInfo {
  name: string;
  language: string | null;
  topics: string[];
}

export interface ScanResult {
  detectedInterests: string[];
  repoCount: number;
  topRepos: RepoInfo[];
}

export interface ApplyRequest {
  tagSlugs: string[];
}

export const onboardingApi = createApi({
  reducerPath: "onboardingApi",
  baseQuery: baseQueryWithRefresh,
  endpoints: (b) => ({
    scanRepos: b.mutation<ScanResult, void>({
      query: () => ({ url: "/api/onboarding/scan", method: "POST" }),
    }),
    applyInterests: b.mutation<{ status: string; count: string }, ApplyRequest>({
      query: (body) => ({ url: "/api/onboarding/apply", method: "POST", body }),
    }),
  }),
});

export const { useScanReposMutation, useApplyInterestsMutation } = onboardingApi;
