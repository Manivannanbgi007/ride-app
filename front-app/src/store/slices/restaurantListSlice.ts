import { createListSlice } from '@/store/slices/createListSlice';
import type { RestaurantFilters } from '@/types/restaurant';
import { RESTAURANT_SORT_OPTIONS } from '@/utils/constants';

/** The restaurant list's filters, order and page, kept while navigating. */
const initialFilters: RestaurantFilters = { sort: RESTAURANT_SORT_OPTIONS[0].value };
const restaurantListSlice = createListSlice('restaurantList', initialFilters);

export const {
  filtersChanged: restaurantFiltersChanged,
  filtersReplaced: restaurantFiltersReplaced,
  filtersCleared: restaurantFiltersCleared,
  pageChanged: restaurantPageChanged,
} = restaurantListSlice.actions;
export const { selectFilters: selectRestaurantFilters, selectPage: selectRestaurantPage } =
  restaurantListSlice.selectors;
export default restaurantListSlice.reducer;
