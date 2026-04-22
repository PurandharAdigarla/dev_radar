import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { InterestCategory, InterestTag, PageResponse } from "./types";

export interface ListTagsArgs {
  q?: string;
  category?: InterestCategory;
  page?: number;
  size?: number;
}

export const interestApi = createApi({
  reducerPath: "interestApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["MyInterests"],
  endpoints: (b) => ({
    listTags: b.query<PageResponse<InterestTag>, ListTagsArgs>({
      query: ({ q, category, page = 0, size = 200 } = {}) => ({
        url: "/api/interest-tags",
        params: {
          ...(q ? { q } : {}),
          ...(category ? { category } : {}),
          page,
          size,
        },
      }),
    }),
    getMyInterests: b.query<InterestTag[], void>({
      query: () => ({ url: "/api/users/me/interests" }),
      providesTags: ["MyInterests"],
    }),
    setMyInterests: b.mutation<InterestTag[], { tagSlugs: string[] }>({
      query: (body) => ({
        url: "/api/users/me/interests",
        method: "PUT",
        body,
      }),
      invalidatesTags: ["MyInterests"],
    }),
  }),
});

export const {
  useListTagsQuery,
  useGetMyInterestsQuery,
  useSetMyInterestsMutation,
} = interestApi;
