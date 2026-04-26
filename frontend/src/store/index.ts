import { configureStore } from "@reduxjs/toolkit";
import { authApi } from "../api/authApi";
import { authReducer } from "../auth/authSlice";
import { interestApi } from "../api/interestApi";
import { radarApi } from "../api/radarApi";
import { actionApi } from "../api/actionApi";
import { observabilityApi } from "../api/observabilityApi";
import { apiKeyApi } from "../api/apiKeyApi";
import { githubApi } from "../api/githubApi";
import { notificationApi } from "../api/notificationApi";
import { engagementApi } from "../api/engagementApi";
import { teamApi } from "../api/teamApi";
import { radarGenerationReducer } from "../radar/radarGenerationSlice";

export function makeStore() {
  return configureStore({
    reducer: {
      auth: authReducer,
      radarGeneration: radarGenerationReducer,
      [authApi.reducerPath]: authApi.reducer,
      [interestApi.reducerPath]: interestApi.reducer,
      [radarApi.reducerPath]: radarApi.reducer,
      [actionApi.reducerPath]: actionApi.reducer,
      [observabilityApi.reducerPath]: observabilityApi.reducer,
      [apiKeyApi.reducerPath]: apiKeyApi.reducer,
      [githubApi.reducerPath]: githubApi.reducer,
      [notificationApi.reducerPath]: notificationApi.reducer,
      [engagementApi.reducerPath]: engagementApi.reducer,
      [teamApi.reducerPath]: teamApi.reducer,
    },
    middleware: (getDefault) =>
      getDefault()
        .concat(authApi.middleware)
        .concat(interestApi.middleware)
        .concat(radarApi.middleware)
        .concat(actionApi.middleware)
        .concat(observabilityApi.middleware)
        .concat(apiKeyApi.middleware)
        .concat(githubApi.middleware)
        .concat(notificationApi.middleware)
        .concat(engagementApi.middleware)
        .concat(teamApi.middleware),
  });
}

export const store = makeStore();

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore["getState"]>;
export type AppDispatch = AppStore["dispatch"];
