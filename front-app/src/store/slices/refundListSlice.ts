import { createListSlice } from '@/store/slices/createListSlice';
import type { RefundFilters } from '@/types/payment';

/** The refund list's filters and page, kept while navigating. */
const initialFilters: RefundFilters = {};
const refundListSlice = createListSlice('refundList', initialFilters);

export const {
  filtersChanged: refundFiltersChanged,
  filtersReplaced: refundFiltersReplaced,
  filtersCleared: refundFiltersCleared,
  pageChanged: refundPageChanged,
} = refundListSlice.actions;
export const { selectFilters: selectRefundFilters, selectPage: selectRefundPage } =
  refundListSlice.selectors;
export default refundListSlice.reducer;
