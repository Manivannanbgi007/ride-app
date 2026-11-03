import { createListSlice } from '@/store/slices/createListSlice';
import type { UserFilters } from '@/types/user';
import { CUSTOMER_SORT_OPTIONS } from '@/utils/constants';

/** The customer list's filters, order and page, kept while navigating. */
const initialFilters: UserFilters = { sort: CUSTOMER_SORT_OPTIONS[0].value };
const customerListSlice = createListSlice('customerList', initialFilters);

export const {
  filtersChanged: customerFiltersChanged,
  filtersCleared: customerFiltersCleared,
  pageChanged: customerPageChanged,
} = customerListSlice.actions;
export const { selectFilters: selectCustomerFilters, selectPage: selectCustomerPage } =
  customerListSlice.selectors;
export default customerListSlice.reducer;
