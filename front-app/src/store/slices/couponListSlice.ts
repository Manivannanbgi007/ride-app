import { createListSlice } from '@/store/slices/createListSlice';
import type { CouponFilters } from '@/types/coupon';
import { COUPON_SORT_OPTIONS } from '@/utils/constants';

/** The coupon list's filters, order and page, kept while navigating. */
const initialFilters: CouponFilters = { sort: COUPON_SORT_OPTIONS[0].value };
const couponListSlice = createListSlice('couponList', initialFilters);

export const {
  filtersChanged: couponFiltersChanged,
  filtersReplaced: couponFiltersReplaced,
  filtersCleared: couponFiltersCleared,
  pageChanged: couponPageChanged,
} = couponListSlice.actions;
export const { selectFilters: selectCouponFilters, selectPage: selectCouponPage } =
  couponListSlice.selectors;
export default couponListSlice.reducer;
