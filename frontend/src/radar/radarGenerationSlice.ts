import { createSlice, type PayloadAction } from "@reduxjs/toolkit";

export interface RadarGenerationState {
  currentGeneratingRadarId: number | null;
  startedAt: string | null;
}

const initialState: RadarGenerationState = {
  currentGeneratingRadarId: null,
  startedAt: null,
};

const slice = createSlice({
  name: "radarGeneration",
  initialState,
  reducers: {
    generationStarted(
      state,
      action: PayloadAction<{ radarId: number; startedAt: string }>,
    ) {
      state.currentGeneratingRadarId = action.payload.radarId;
      state.startedAt = action.payload.startedAt;
    },
    generationFinished(state) {
      state.currentGeneratingRadarId = null;
      state.startedAt = null;
    },
  },
});

export const { generationStarted, generationFinished } = slice.actions;
export const radarGenerationReducer = slice.reducer;
