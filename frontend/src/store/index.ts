import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import { authReducer } from "../auth/authSlice";
import { topicApi } from "../api/topicApi";
import { radarApi } from "../api/radarApi";
import { engagementApi } from "../api/engagementApi";
import { radarGenerationReducer } from "../radar/radarGenerationSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      radarGeneration: radarGenerationReducer,
      [authApi.reducerPath]: authApi.reducer,
      [topicApi.reducerPath]: topicApi.reducer,
      [radarApi.reducerPath]: radarApi.reducer,
      [engagementApi.reducerPath]: engagementApi.reducer,
    },
    middleware: (getDefault) =>
      getDefault()
        .concat(authApi.middleware)
        .concat(topicApi.middleware)
        .concat(radarApi.middleware)
        .concat(engagementApi.middleware),
  });
}

export const store = makeStore();

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
