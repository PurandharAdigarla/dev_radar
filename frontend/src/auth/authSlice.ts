import { createSlice, PayloadAction } from "@reduxjs/toolkit";
import type { User } from "../api/types";
import { tokenStorage } from "./tokenStorage";

export interface AuthState {
  accessToken: string | null;
  user: User | null;
}

const initialState: AuthState = {
  accessToken: tokenStorage.getAccess(),
  user: null,
};

const slice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    loginSucceeded(state, action: PayloadAction<{ accessToken: string; user: User }>) {
      state.accessToken = action.payload.accessToken;
      state.user = action.payload.user;
    },
    setUser(state, action: PayloadAction<User>) {
      state.user = action.payload;
    },
    tokenRefreshed(state, action: PayloadAction<{ accessToken: string }>) {
      state.accessToken = action.payload.accessToken;
    },
    loggedOut(state) {
      state.accessToken = null;
      state.user = null;
    },
  },
});

export const { loginSucceeded, setUser, tokenRefreshed, loggedOut } = slice.actions;
export const authReducer = slice.reducer;
