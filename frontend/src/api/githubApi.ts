import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { GitHubStatus } from "./types";

export const githubApi = createApi({
  reducerPath: "githubApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["GitHubStatus"],
  endpoints: (b) => ({
    status: b.query<GitHubStatus, void>({
      query: () => ({ url: "/api/github/status" }),
      providesTags: ["GitHubStatus"],
    }),
  }),
});

export const { useStatusQuery: useGitHubStatusQuery } = githubApi;
