import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { RadarSummary } from "./types";

export interface TeamDTO {
  id: number;
  name: string;
  slug: string;
  plan: string;
  ownerId: number;
  memberCount: number;
}

export interface TeamMemberDTO {
  userId: number;
  role: string;
  joinedAt: string;
}

export interface CreateTeamRequest {
  name: string;
}

export interface AddTeamMemberRequest {
  userId: number;
  role: string;
}

export const teamApi = createApi({
  reducerPath: "teamApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Team", "TeamList", "TeamMembers", "TeamRadars"],
  endpoints: (b) => ({
    listTeams: b.query<TeamDTO[], void>({
      query: () => ({ url: "/api/teams" }),
      providesTags: ["TeamList"],
    }),
    getTeam: b.query<TeamDTO, number>({
      query: (id) => ({ url: `/api/teams/${id}` }),
      providesTags: (_r, _e, id) => [{ type: "Team", id }],
    }),
    createTeam: b.mutation<TeamDTO, CreateTeamRequest>({
      query: (body) => ({ url: "/api/teams", method: "POST", body }),
      invalidatesTags: ["TeamList"],
    }),
    listMembers: b.query<TeamMemberDTO[], number>({
      query: (teamId) => ({ url: `/api/teams/${teamId}/members` }),
      providesTags: (_r, _e, teamId) => [{ type: "TeamMembers", id: teamId }],
    }),
    addMember: b.mutation<TeamMemberDTO, { teamId: number } & AddTeamMemberRequest>({
      query: ({ teamId, ...body }) => ({
        url: `/api/teams/${teamId}/members`,
        method: "POST",
        body,
      }),
      invalidatesTags: (_r, _e, { teamId }) => [
        { type: "TeamMembers", id: teamId },
        { type: "Team", id: teamId },
      ],
    }),
    removeMember: b.mutation<void, { teamId: number; userId: number }>({
      query: ({ teamId, userId }) => ({
        url: `/api/teams/${teamId}/members/${userId}`,
        method: "DELETE",
      }),
      invalidatesTags: (_r, _e, { teamId }) => [
        { type: "TeamMembers", id: teamId },
        { type: "Team", id: teamId },
      ],
    }),
    listTeamRadars: b.query<RadarSummary[], number>({
      query: (teamId) => ({ url: `/api/teams/${teamId}/radars` }),
      providesTags: (_r, _e, teamId) => [{ type: "TeamRadars", id: teamId }],
    }),
    generateTeamRadar: b.mutation<RadarSummary, number>({
      query: (teamId) => ({
        url: `/api/teams/${teamId}/radars`,
        method: "POST",
      }),
      invalidatesTags: (_r, _e, teamId) => [{ type: "TeamRadars", id: teamId }],
    }),
  }),
});

export const {
  useListTeamsQuery,
  useGetTeamQuery,
  useCreateTeamMutation,
  useListMembersQuery,
  useAddMemberMutation,
  useRemoveMemberMutation,
  useListTeamRadarsQuery,
  useGenerateTeamRadarMutation,
} = teamApi;
