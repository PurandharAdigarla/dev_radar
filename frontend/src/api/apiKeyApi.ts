import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { ApiKeySummary, ApiKeyCreateRequest, ApiKeyCreateResponse } from "./types";

export const apiKeyApi = createApi({
  reducerPath: "apiKeyApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["ApiKey"],
  endpoints: (b) => ({
    list: b.query<ApiKeySummary[], void>({
      query: () => ({ url: "/api/users/me/api-keys" }),
      providesTags: ["ApiKey"],
    }),
    create: b.mutation<ApiKeyCreateResponse, ApiKeyCreateRequest>({
      query: (body) => ({
        url: "/api/users/me/api-keys",
        method: "POST",
        body,
      }),
      invalidatesTags: ["ApiKey"],
    }),
    revoke: b.mutation<void, number>({
      query: (id) => ({
        url: `/api/users/me/api-keys/${id}`,
        method: "DELETE",
      }),
      invalidatesTags: ["ApiKey"],
    }),
  }),
});

export const {
  useListQuery: useListApiKeysQuery,
  useCreateMutation: useCreateApiKeyMutation,
  useRevokeMutation: useRevokeApiKeyMutation,
} = apiKeyApi;
