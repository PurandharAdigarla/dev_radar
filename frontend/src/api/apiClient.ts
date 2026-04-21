import {
  fetchBaseQuery,
  type BaseQueryFn,
  type FetchArgs,
  type FetchBaseQueryError,
} from "@reduxjs/toolkit/query";
import { tokenStorage } from "../auth/tokenStorage";
import { loggedOut, tokenRefreshed } from "../auth/authSlice";
import type { RefreshResponse } from "./types";

const baseUrl =
  typeof window !== "undefined" && window.location?.origin
    ? `${window.location.origin}/`
    : "/";

const rawBaseQuery = fetchBaseQuery({
  baseUrl,
  prepareHeaders: (headers) => {
    const token = tokenStorage.getAccess();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return headers;
  },
});

export const baseQueryWithRefresh: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  let result = await rawBaseQuery(args, api, extraOptions);

  if (result.error?.status === 401) {
    const refreshToken = tokenStorage.getRefresh();
    if (!refreshToken) {
      api.dispatch(loggedOut());
      tokenStorage.clear();
      return result;
    }

    const refreshResult = await rawBaseQuery(
      {
        url: "/api/auth/refresh",
        method: "POST",
        body: { refreshToken },
      },
      api,
      extraOptions,
    );

    if (refreshResult.data) {
      const data = refreshResult.data as RefreshResponse;
      tokenStorage.setAccess(data.accessToken);
      tokenStorage.setRefresh(data.refreshToken);
      api.dispatch(tokenRefreshed({ accessToken: data.accessToken }));
      result = await rawBaseQuery(args, api, extraOptions);
    } else {
      api.dispatch(loggedOut());
      tokenStorage.clear();
    }
  }

  return result;
};
