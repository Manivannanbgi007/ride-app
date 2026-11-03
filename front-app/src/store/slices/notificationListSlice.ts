import { createListSlice } from '@/store/slices/createListSlice';
import type { NotificationFilters } from '@/types/notification';

/** The admin notification list's filters and page, kept while navigating. */
const initialFilters: NotificationFilters = {};
const notificationListSlice = createListSlice('notificationList', initialFilters);

export const {
  filtersChanged: notificationFiltersChanged,
  filtersReplaced: notificationFiltersReplaced,
  filtersCleared: notificationFiltersCleared,
  pageChanged: notificationPageChanged,
} = notificationListSlice.actions;
export const { selectFilters: selectNotificationFilters, selectPage: selectNotificationPage } =
  notificationListSlice.selectors;
export default notificationListSlice.reducer;
