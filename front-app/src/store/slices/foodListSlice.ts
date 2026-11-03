import { createListSlice } from '@/store/slices/createListSlice';
import type { MenuItemFilters } from '@/types/catalog';
import { FOOD_SORT_OPTIONS } from '@/utils/constants';

/** The food (menu item) list's filters, order and page, kept while navigating. */
const initialFilters: MenuItemFilters = { sort: FOOD_SORT_OPTIONS[0].value };
const foodListSlice = createListSlice('foodList', initialFilters);

export const {
  filtersChanged: foodFiltersChanged,
  filtersCleared: foodFiltersCleared,
  pageChanged: foodPageChanged,
} = foodListSlice.actions;
export const { selectFilters: selectFoodFilters, selectPage: selectFoodPage } =
  foodListSlice.selectors;
export default foodListSlice.reducer;
