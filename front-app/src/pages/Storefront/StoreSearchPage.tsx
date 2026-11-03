import { FoodHitList, RestaurantCard } from '@/components/business/Storefront';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { EmptyState } from '@/components/common/EmptyState';
import { TableLoader } from '@/components/common/Loader';
import { PageHeader } from '@/components/common/PageHeader';
import { Pagination } from '@/components/common/Pagination';
import { SearchInput } from '@/components/common/SearchInput';
import { Select } from '@/components/common/Select';
import { TabPanel, Tabs } from '@/components/common/Tabs';
import {
  useFoodSearch,
  useRestaurantSearch,
  useStoreCategories,
} from '@/hooks/api/storefrontHooks';
import { pageFromParam, pageToParam, useQueryParams } from '@/hooks/useQueryParams';
import { useTabParam } from '@/hooks/useTabParam';
import type { FoodType } from '@/types/catalog';
import type { FoodSort, RestaurantSort, SearchResults } from '@/types/storefront';
import { cn } from '@/utils/classNames';
import { FOOD_TYPE_LABELS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatNumber } from '@/utils/formatUtils';
import { ratingOfHit } from '@/utils/ratingUtils';
import { oneOf, toIdFilter } from '@/utils/validationUtils';

const PAGE_SIZE = 12;
const TAB_IDS = ['restaurants', 'dishes'] as const;
const PARAM_KEYS = [
  'q',
  'city',
  'cuisine',
  'categoryId',
  'foodType',
  'minRating',
  'sort',
  'page',
] as const;
const FOOD_TYPES: readonly FoodType[] = ['VEG', 'NON_VEG', 'EGG'];
const FOOD_SORTS: readonly FoodSort[] = [
  'RELEVANCE',
  'PRICE_LOW_TO_HIGH',
  'PRICE_HIGH_TO_LOW',
  'RATING',
];
const SORT_OPTIONS = [
  { value: 'RELEVANCE', label: 'Best match' },
  { value: 'RATING', label: 'Top rated' },
  { value: 'PRICE_LOW_TO_HIGH', label: 'Price: low to high' },
  { value: 'PRICE_HIGH_TO_LOW', label: 'Price: high to low' },
];
const RESTAURANT_SORTS: readonly RestaurantSort[] = ['RELEVANCE', 'RATING'];
const RESTAURANT_SORT_OPTIONS = SORT_OPTIONS.filter((option) =>
  RESTAURANT_SORTS.includes(option.value as RestaurantSort),
);
/** "Rated at least": the values are what the API is asked with; unrated restaurants and dishes are left out. */
const MIN_RATINGS = ['3', '4', '4.5'] as const;
const MIN_RATING_OPTIONS = MIN_RATINGS.map((value) => ({ value, label: `${value} stars and up` }));
const toMinRating = (value: string | undefined): number | undefined => {
  const chosen = oneOf(value ?? '', MIN_RATINGS);
  return chosen === undefined ? undefined : Number(chosen);
};

type QueryParams = ReturnType<typeof useQueryParams<(typeof PARAM_KEYS)[number]>>;

/**
 * Search (search-service): restaurants by name, cuisine or description, and dishes by name, with filters. It
 * forgives typos and matches the start of words. The text, filters and tab are in the address.
 */
export default function StoreSearchPage() {
  const [params, setParams] = useQueryParams(PARAM_KEYS);
  const [tab, setTab] = useTabParam(TAB_IDS);

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={params.q ? `Results for "${params.q}"` : 'Search'}
        subtitle="Restaurants and dishes; spelling mistakes are forgiven"
      />
      <Card>
        <div className="grid items-start gap-4 sm:grid-cols-[2fr_1fr]">
          <SearchInput
            label="Search"
            placeholder="A dish, a cuisine or a restaurant"
            maxLength={100}
            value={params.q ?? ''}
            onSearch={(text) => setParams({ q: text })}
          />
          <SearchInput
            label="City"
            placeholder="Any city"
            maxLength={100}
            value={params.city ?? ''}
            onSearch={(text) => setParams({ city: text })}
          />
        </div>
      </Card>
      <Tabs
        label="Search results"
        activeId={tab}
        // A tab keeps the text and city; the other filters belong to one tab
        onChange={(next) => {
          setParams({
            cuisine: undefined,
            categoryId: undefined,
            foodType: undefined,
            minRating: undefined,
            sort: undefined,
          });
          setTab(next);
        }}
        tabs={[
          { id: 'restaurants', label: 'Restaurants' },
          { id: 'dishes', label: 'Dishes' },
        ]}
      />
      <TabPanel id={tab}>
        {tab === 'restaurants' ? (
          <RestaurantResults params={params} setParams={setParams} />
        ) : (
          <DishResults params={params} setParams={setParams} />
        )}
      </TabPanel>
    </div>
  );
}

interface ResultsProps {
  params: QueryParams[0];
  setParams: QueryParams[1];
}

function ResultsPager({
  results,
  onPageChange,
}: {
  results: SearchResults<unknown, unknown>;
  onPageChange: (page: number) => void;
}) {
  return (
    <Pagination
      page={results.page}
      size={results.size}
      // search-service pages through the first 10,000 results at most
      totalPages={Math.min(results.totalPages, Math.floor(10_000 / results.size))}
      totalElements={results.totalHits}
      onPageChange={onPageChange}
    />
  );
}

function RestaurantResults({ params, setParams }: ResultsProps) {
  const sort = oneOf(params.sort ?? '', RESTAURANT_SORTS);
  const minRating = toMinRating(params.minRating);
  const search = useRestaurantSearch(
    { q: params.q, city: params.city, cuisine: params.cuisine, minRating, sort },
    pageFromParam(params.page),
    PAGE_SIZE,
  );
  const data = search.data;
  const hasFilters = minRating !== undefined;

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Select
          label="Order"
          options={RESTAURANT_SORT_OPTIONS}
          value={sort ?? 'RELEVANCE'}
          onChange={(event) => setParams({ sort: event.target.value })}
        />
        <Select
          label="Rating"
          placeholder="Any rating"
          options={MIN_RATING_OPTIONS}
          value={params.minRating ?? ''}
          onChange={(event) => setParams({ minRating: event.target.value })}
        />
      </div>
      <AsyncContent
        isLoading={search.isPending}
        error={search.error}
        isEmpty={data?.results.length === 0}
        errorTitle={ERROR_MESSAGES.STORE_SEARCH_FAILED}
        onRetry={() => void search.refetch()}
        isRetrying={search.isFetching}
        loading={<TableLoader columns={3} />}
        empty={
          <EmptyState
            title="No restaurants found"
            message={
              hasFilters
                ? 'No restaurant has this rating yet. Try a lower rating, other words or another city.'
                : 'Try other words or another city.'
            }
            action={
              hasFilters && (
                <ClearFiltersButton size="sm" onClick={() => setParams({ minRating: undefined })} />
              )
            }
          />
        }
      >
        {data && (
          <div className="flex flex-col gap-6">
            {data.facets.cuisines.length > 0 && (
              <div className="flex flex-wrap gap-2" role="group" aria-label="Cuisines">
                {data.facets.cuisines.map((facet) => {
                  const chosen = params.cuisine?.toLowerCase() === facet.value.toLowerCase();
                  return (
                    <button
                      key={facet.value}
                      type="button"
                      aria-pressed={chosen}
                      onClick={() => setParams({ cuisine: chosen ? undefined : facet.value })}
                      className={cn(
                        'rounded-full border px-4 py-1.5 text-sm',
                        chosen
                          ? 'border-brand-500 bg-brand-500 text-on-brand'
                          : 'border-line bg-surface hover:border-brand-500',
                      )}
                    >
                      {facet.value}{' '}
                      <span className="opacity-70">({formatNumber(facet.count)})</span>
                    </button>
                  );
                })}
              </div>
            )}
            <div
              className={cn(
                'grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4',
                search.isPlaceholderData && 'opacity-60',
              )}
            >
              {data.results.map((restaurant) => (
                <RestaurantCard
                  key={restaurant.id}
                  id={restaurant.id}
                  name={restaurant.name}
                  cuisines={restaurant.cuisines}
                  place={restaurant.addressLine}
                  distanceKm={restaurant.distanceKm}
                  rating={ratingOfHit(restaurant)}
                />
              ))}
            </div>
            <ResultsPager
              results={data}
              onPageChange={(page) => setParams({ page: pageToParam(page) })}
            />
          </div>
        )}
      </AsyncContent>
    </div>
  );
}

function DishResults({ params, setParams }: ResultsProps) {
  const categories = useStoreCategories();
  const foodType = oneOf(params.foodType ?? '', FOOD_TYPES);
  const sort = oneOf(params.sort ?? '', FOOD_SORTS);
  const minRating = toMinRating(params.minRating);
  const search = useFoodSearch(
    {
      q: params.q,
      city: params.city,
      categoryId: toIdFilter(params.categoryId ?? ''),
      foodType,
      minRating,
      sort,
    },
    pageFromParam(params.page),
    PAGE_SIZE,
  );
  const data = search.data;
  const hasFilters = Boolean(params.categoryId ?? params.foodType ?? params.minRating);

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Select
          label="Type"
          placeholder="Veg and non-veg"
          options={FOOD_TYPES.map((type) => ({ value: type, label: FOOD_TYPE_LABELS[type] }))}
          value={foodType ?? ''}
          onChange={(event) => setParams({ foodType: event.target.value })}
        />
        <Select
          label="Category"
          placeholder="All categories"
          options={(categories.data ?? []).map((category) => ({
            value: String(category.id),
            label: category.name,
          }))}
          value={params.categoryId ?? ''}
          onChange={(event) => setParams({ categoryId: event.target.value })}
        />
        <Select
          label="Rating"
          placeholder="Any rating"
          options={MIN_RATING_OPTIONS}
          value={params.minRating ?? ''}
          onChange={(event) => setParams({ minRating: event.target.value })}
        />
        <Select
          label="Order"
          options={SORT_OPTIONS}
          value={sort ?? 'RELEVANCE'}
          onChange={(event) => setParams({ sort: event.target.value })}
        />
      </div>
      <AsyncContent
        isLoading={search.isPending}
        error={search.error}
        isEmpty={data?.results.length === 0}
        errorTitle={ERROR_MESSAGES.STORE_SEARCH_FAILED}
        onRetry={() => void search.refetch()}
        isRetrying={search.isFetching}
        loading={<TableLoader columns={3} />}
        empty={
          <EmptyState
            title="No dishes found"
            message="Only dishes that can be ordered right now are shown."
            action={
              hasFilters && (
                <ClearFiltersButton
                  size="sm"
                  onClick={() =>
                    setParams({ categoryId: undefined, foodType: undefined, minRating: undefined })
                  }
                />
              )
            }
          />
        }
      >
        {data && (
          <div className={cn('flex flex-col gap-6', search.isPlaceholderData && 'opacity-60')}>
            <FoodHitList foods={data.results} />
            <ResultsPager
              results={data}
              onPageChange={(page) => setParams({ page: pageToParam(page) })}
            />
          </div>
        )}
      </AsyncContent>
    </div>
  );
}
