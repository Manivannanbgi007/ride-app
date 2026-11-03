import { createListSlice } from '@/store/slices/createListSlice';
import type { PaymentFilters } from '@/types/payment';

/** The payment list's filters and page, kept while navigating; other pages open it filtered ("payments of order 42"). */
const initialFilters: PaymentFilters = {};
const paymentListSlice = createListSlice('paymentList', initialFilters);

export const {
  filtersChanged: paymentFiltersChanged,
  filtersReplaced: paymentFiltersReplaced,
  filtersCleared: paymentFiltersCleared,
  pageChanged: paymentPageChanged,
} = paymentListSlice.actions;
export const { selectFilters: selectPaymentFilters, selectPage: selectPaymentPage } =
  paymentListSlice.selectors;
export default paymentListSlice.reducer;
