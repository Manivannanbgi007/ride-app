import { createListSlice } from '@/store/slices/createListSlice';
import type { PartnerFilters } from '@/types/delivery';

/** The delivery partner list's filters and page, kept while navigating. */
const initialFilters: PartnerFilters = {};
const partnerListSlice = createListSlice('partnerList', initialFilters);

export const {
  filtersChanged: partnerFiltersChanged,
  filtersReplaced: partnerFiltersReplaced,
  filtersCleared: partnerFiltersCleared,
  pageChanged: partnerPageChanged,
} = partnerListSlice.actions;
export const { selectFilters: selectPartnerFilters, selectPage: selectPartnerPage } =
  partnerListSlice.selectors;
export default partnerListSlice.reducer;
