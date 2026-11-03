import { RestaurantFilterBar, RestaurantTable } from '@/components/business/Restaurant';
import { Card } from '@/components/common/Card';
import { PageHeader } from '@/components/common/PageHeader';
import { PagedResults } from '@/components/common/PagedResults';
import { useRestaurants } from '@/hooks/api/restaurantHooks';
import { useAppDispatch, useAppSelector } from '@/store/hooks';
import {
  restaurantFiltersChanged,
  restaurantFiltersCleared,
  restaurantPageChanged,
  selectRestaurantFilters,
  selectRestaurantPage,
} from '@/store/slices/restaurantListSlice';
import { DEFAULT_PAGE_SIZE } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

/** "Restaurants": every restaurant, the ones waiting for approval first when asked. */
export default function RestaurantListPage() {
  const dispatch = useAppDispatch();
  const filters = useAppSelector(selectRestaurantFilters);
  const page = useAppSelector(selectRestaurantPage);
  const restaurants = useRestaurants(filters, page, DEFAULT_PAGE_SIZE);
  const clearFilters = () => dispatch(restaurantFiltersCleared());

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Restaurants"
        subtitle="Approve new restaurants, and activate, deactivate or suspend them"
      />

      <Card>
        <RestaurantFilterBar
          filters={filters}
          onChange={(changes) => dispatch(restaurantFiltersChanged(changes))}
          onClear={clearFilters}
        />
      </Card>

      <Card>
        <PagedResults
          query={restaurants}
          errorTitle={ERROR_MESSAGES.RESTAURANTS_LOAD_FAILED}
          emptyTitle="No restaurants yet"
          filteredEmptyTitle="No restaurants match these filters"
          hasFilters={Boolean(filters.search || filters.status || filters.city)}
          onClearFilters={clearFilters}
          onPageChange={(newPage) => dispatch(restaurantPageChanged(newPage))}
        >
          {(rows, isUpdating) => <RestaurantTable restaurants={rows} isUpdating={isUpdating} />}
        </PagedResults>
      </Card>
    </div>
  );
}
