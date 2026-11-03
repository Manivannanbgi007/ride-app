import { createListSlice } from '@/store/slices/createListSlice';
import type { OrderFilters } from '@/types/order';

/**
 * The order list's filters and page. In Redux so they are still there after opening an order and coming back,
 * and so other pages can open the list already filtered ("all orders of this customer").
 */
const initialFilters: OrderFilters = {};
const orderListSlice = createListSlice('orderList', initialFilters);

export const {
  filtersChanged: orderFiltersChanged,
  filtersReplaced: orderFiltersReplaced,
  filtersCleared: orderFiltersCleared,
  pageChanged: orderPageChanged,
} = orderListSlice.actions;
export const { selectFilters: selectOrderFilters, selectPage: selectOrderPage } =
  orderListSlice.selectors;
export default orderListSlice.reducer;
