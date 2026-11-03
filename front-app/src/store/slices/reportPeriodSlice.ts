import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { DashboardPeriodDays } from '@/utils/constants';

/**
 * The period the admin looks at ("Filter Period"). Kept in Redux so it stays the same while navigating: going
 * to another page and back does not reset it, and the analytics pages (Phase 16c) use the same choice.
 */
interface ReportPeriodState {
  days: DashboardPeriodDays;
}

const initialState: ReportPeriodState = {
  days: 7,
};

const reportPeriodSlice = createSlice({
  name: 'reportPeriod',
  initialState,
  reducers: {
    periodDaysChanged(state, action: PayloadAction<DashboardPeriodDays>) {
      state.days = action.payload;
    },
  },
  selectors: {
    selectPeriodDays: (state) => state.days,
  },
});

export const { periodDaysChanged } = reportPeriodSlice.actions;
export const { selectPeriodDays } = reportPeriodSlice.selectors;
export default reportPeriodSlice.reducer;
