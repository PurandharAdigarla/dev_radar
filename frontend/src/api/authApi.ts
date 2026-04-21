import { createApi } from "@reduxjs/toolkit/query/react";
import { baseQueryWithRefresh } from "./apiClient";
import type { LoginRequest, LoginResponse, RegisterRequest, User } from "./types";

export const authApi = createApi({
  reducerPath: "authApi",
  baseQuery: baseQueryWithRefresh,
  endpoints: (b) => ({
    login: b.mutation<LoginResponse, LoginRequest>({
      query: (body) => ({ url: "/api/auth/login", method: "POST", body }),
    }),
    register: b.mutation<{ userId: number }, RegisterRequest>({
      query: (body) => ({ url: "/api/auth/register", method: "POST", body }),
    }),
    me: b.query<User, void>({
      query: () => ({ url: "/api/users/me" }),
    }),
  }),
});

export const { useLoginMutation, useRegisterMutation, useMeQuery } = authApi;
