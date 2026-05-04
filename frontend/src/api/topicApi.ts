import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";

export interface Topic {
  id: number;
  topic: string;
  displayOrder: number;
}

export const topicApi = createApi({
  reducerPath: "topicApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["Topics"],
  endpoints: (b) => ({
    getTopics: b.query<Topic[], void>({
      query: () => ({ url: "/api/users/me/topics" }),
      providesTags: ["Topics"],
    }),
    setTopics: b.mutation<Topic[], { topics: string[] }>({
      query: (body) => ({
        url: "/api/users/me/topics",
        method: "PUT",
        body,
      }),
      invalidatesTags: ["Topics"],
    }),
  }),
});

export const { useGetTopicsQuery, useSetTopicsMutation } = topicApi;
