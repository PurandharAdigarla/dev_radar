import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import { authReducer } from "../auth/authSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      [authApi.reducerPath]: authApi.reducer,
    },
    middleware: (getDefault) => getDefault().concat(authApi.middleware),
  });
}

export const store = makeStore();

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
