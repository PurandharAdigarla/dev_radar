import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { ActionProposal } from "./types";

export const actionApi = createApi({
  reducerPath: "actionApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Proposals"],
  endpoints: (b) => ({
    listByRadar: b.query<ActionProposal[], number>({
      query: (radarId) => ({ url: "/api/actions/proposals", params: { radar_id: radarId } }),
      providesTags: (_r, _e, radarId) => [{ type: "Proposals", id: radarId }],
    }),
    approve: b.mutation<ActionProposal, { id: number; fixVersion: string }>({
      query: ({ id, fixVersion }) => ({
        url: `/api/actions/${id}/approve`,
        method: "POST",
        body: { fix_version: fixVersion },
      }),
      invalidatesTags: (result) =>
        result ? [{ type: "Proposals", id: result.radarId }] : [],
    }),
    dismiss: b.mutation<ActionProposal, number>({
      query: (id) => ({ url: `/api/actions/${id}`, method: "DELETE" }),
      invalidatesTags: (result) =>
        result ? [{ type: "Proposals", id: result.radarId }] : [],
    }),
  }),
});

export const {
  useListByRadarQuery: useListProposalsByRadarQuery,
  useApproveMutation: useApproveProposalMutation,
  useDismissMutation: useDismissProposalMutation,
} = actionApi;
