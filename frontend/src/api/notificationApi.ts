import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { NotificationPreference } from "./types";

export const notificationApi = createApi({
  reducerPath: "notificationApi",
  baseQuery: baseQueryWithRefresh,
  tagTypes: ["NotificationPref"],
  endpoints: (b) => ({
    getPrefs: b.query<NotificationPreference, void>({
      query: () => ({ url: "/api/users/me/notifications" }),
      providesTags: ["NotificationPref"],
    }),
    updatePrefs: b.mutation<NotificationPreference, NotificationPreference>({
      query: (body) => ({
        url: "/api/users/me/notifications",
        method: "PUT",
        body,
      }),
      invalidatesTags: ["NotificationPref"],
    }),
    sendTestEmail: b.mutation<void, void>({
      query: () => ({
        url: "/api/users/me/notifications/test-email",
        method: "POST",
      }),
    }),
  }),
});

export const {
  useGetPrefsQuery: useGetNotificationPrefsQuery,
  useUpdatePrefsMutation: useUpdateNotificationPrefsMutation,
  useSendTestEmailMutation,
} = notificationApi;
