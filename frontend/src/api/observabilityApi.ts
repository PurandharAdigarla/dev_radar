import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import type { ObservabilitySummary, MetricsDay } from "./types";

const baseUrl =
  typeof window !== "undefined" && window.location?.origin
    ? `${window.location.origin}/`
    : "/";

export const observabilityApi = createApi({
  reducerPath: "observabilityApi",
  baseQuery: fetchBaseQuery({ baseUrl }),
  endpoints: (b) => ({
    getSummary: b.query<ObservabilitySummary, void>({
      query: () => ({ url: "/api/observability/summary" }),
    }),
    getTimeseries: b.query<MetricsDay[], number>({
      query: (days) => ({
        url: "/api/observability/timeseries",
        params: { days },
      }),
    }),
  }),
});

export const { useGetSummaryQuery, useGetTimeseriesQuery } = observabilityApi;
