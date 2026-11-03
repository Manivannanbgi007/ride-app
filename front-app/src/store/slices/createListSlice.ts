import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import { withoutEmptyValues } from '@/utils/objectUtils';

/** What every admin list keeps in Redux: its filters (and sort order) and the page shown. */
export interface ListState<F> {
  filters: F;
  /** Zero-based, as the APIs count */
  page: number;
}

/**
 * Builds the Redux slice of an admin list. Every list works the same way, so this is written once:
 *
 *   const initialFilters: OrderFilters = {};
 *   const orderListSlice = createListSlice('orderList', initialFilters);
 *
 * Actions: filtersChanged (some filters; back to the first page), filtersReplaced (all filters, e.g. "orders of
 * customer 7" from another page), filtersCleared (back to `initialFilters`), pageChanged.
 * Empty values (undefined, '') are dropped, so "no status chosen" is one state (and one React Query key).
 * (The reducers return a new state instead of changing it: simpler for TypeScript with any filter type.)
 */
export function createListSlice<F extends object, Name extends string>(
  name: Name,
  initialFilters: F,
) {
  const initialState: ListState<F> = { filters: initialFilters, page: 0 };

  return createSlice({
    name,
    initialState,
    reducers: {
      filtersChanged: (state, action: PayloadAction<Partial<F>>): ListState<F> => ({
        filters: withoutEmptyValues({ ...(state.filters as F), ...action.payload }),
        page: 0,
      }),
      filtersReplaced: (_state, action: PayloadAction<F>): ListState<F> => ({
        filters: withoutEmptyValues({ ...initialFilters, ...action.payload }),
        page: 0,
      }),
      filtersCleared: (): ListState<F> => initialState,
      pageChanged: (state, action: PayloadAction<number>): ListState<F> => ({
        filters: state.filters as F,
        page: action.payload,
      }),
    },
    selectors: {
      selectFilters: (state) => state.filters,
      selectPage: (state) => state.page,
    },
  });
}
