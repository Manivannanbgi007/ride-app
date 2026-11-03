import { Link } from 'react-router';

import { RestaurantCard } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { useRestaurantRatings } from '@/hooks/api/ratingHooks';
import { useStoreRestaurants } from '@/hooks/api/storefrontHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import type { StoreRestaurantFilters } from '@/types/storefront';
import { CUSTOMER_ROUTES } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { ratingOf } from '@/utils/ratingUtils';
import { formatNumber } from '@/utils/formatUtils';

const PAGE_SIZE = 12;
const SORT_OPTIONS = [
  { value: 'name', label: 'Name A-Z' },
  { value: 'createdAt,desc', label: 'Newest first' },
];
const FILTER_KEYS = ['search', 'city', 'cuisine', 'sort', 'page'] as const;

/** Every restaurant taking orders, with filters kept in the address (?city=Pune&cuisine=Biryani). */
export default function RestaurantsPage() {
  const [params, setParams] = useQueryParams(FILTER_KEYS);
  const filters: StoreRestaurantFilters = {
    search: params.search,
    city: params.city,
    cuisine: params.cuisine,
    sort: params.sort,
  };
  const page = pageFromParam(params.page);
  const restaurants = useStoreRestaurants(filters, page, PAGE_SIZE);
  const ratings = useRestaurantRatings(
    restaurants.data?.content.map((restaurant) => restaurant.id) ?? [],
  );
  const hasFilters = Boolean(params.search ?? params.city ?? params.cuisine);
  const clearFilters = () =>
    setParams({ search: undefined, city: undefined, cuisine: undefined, sort: undefined });

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Restaurants"
        subtitle={
          restaurants.data
            ? `${formatNumber(restaurants.data.totalElements)} restaurants taking orders`
            : 'Restaurants taking orders'
        }
        actions={
          <Link
            to={`${CUSTOMER_ROUTES.search}?tab=restaurants&sort=RATING`}
            className="text-sm font-semibold text-brand-text hover:underline"
          >
            See top rated restaurants
          </Link>
        }
      />
      <Card>
        <div className="grid items-start gap-4 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr_auto]">
          <SearchInput
            label="Restaurant name"
            placeholder="Part of the name"
            maxLength={100}
            value={params.search ?? ''}
            onSearch={(text) => setParams({ search: text })}
          />
          <SearchInput
            label="City"
            placeholder="e.g. Bengaluru"
            maxLength={100}
            value={params.city ?? ''}
            onSearch={(text) => setParams({ city: text })}
          />
          <SearchInput
            label="Cuisine"
            placeholder="e.g. Biryani"
            maxLength={40}
            value={params.cuisine ?? ''}
            onSearch={(text) => setParams({ cuisine: text })}
          />
          <Select
            label="Order"
            options={SORT_OPTIONS}
            value={params.sort ?? 'name'}
            onChange={(event) => setParams({ sort: event.target.value })}
          />
          <div className="flex items-end sm:pt-7">
            <ClearFiltersButton onClick={clearFilters} disabled={!hasFilters} />
          </div>
        </div>
      </Card>

      <AsyncContent
        isLoading={restaurants.isPending}
        error={restaurants.error}
        isEmpty={restaurants.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.STORE_RESTAURANTS_LOAD_FAILED}
        onRetry={() => void restaurants.refetch()}
        isRetrying={restaurants.isFetching}
        loading={
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }, (_, index) => (
              <Skeleton key={index} className="h-60 rounded-2xl bg-surface" />
            ))}
          </div>
        }
        empty={
          <EmptyState
            title={hasFilters ? 'No restaurants match these filters' : 'No restaurants yet'}
            message={hasFilters ? 'The city must be written in full, e.g. "Bengaluru".' : undefined}
            action={hasFilters && <ClearFiltersButton size="sm" onClick={clearFilters} />}
          />
        }
      >
        {restaurants.data && (
          <div className="flex flex-col gap-6">
            <div
              className={
                restaurants.isPlaceholderData
                  ? 'grid gap-6 opacity-60 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'
                  : 'grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'
              }
            >
              {restaurants.data.content.map((restaurant) => (
                <RestaurantCard
                  key={restaurant.id}
                  id={restaurant.id}
                  name={restaurant.name}
                  cuisines={restaurant.cuisines}
                  place={restaurant.city}
                  openNow={restaurant.openNow}
                  rating={ratingOf(ratings, restaurant.id)}
                />
              ))}
            </div>
            <Pagination
              page={restaurants.data.page}
              size={restaurants.data.size}
              totalPages={restaurants.data.totalPages}
              totalElements={restaurants.data.totalElements}
              onPageChange={(newPage) => setParams({ page: pageToParam(newPage) })}
            />
          </div>
        )}
      </AsyncContent>
    </div>
  );
}
