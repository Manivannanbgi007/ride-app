import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';

import { catalogService } from '@/services/catalogService';
import { restaurantService } from '@/services/restaurantService';
import { searchService } from '@/services/searchService';
import type {
  FoodSearchParams,
  RestaurantSearchParams,
  StoreRestaurantFilters,
} from '@/types/storefront';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** Public data changes slowly (the services cache it for 10 minutes too). */
const STOREFRONT_STALE_MS = 60_000;

export function useStoreRestaurants(filters: StoreRestaurantFilters, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.restaurants(filters, page, size),
    queryFn: () => restaurantService.browseRestaurants(filters, page, size),
    placeholderData: keepPreviousData,
    staleTime: STOREFRONT_STALE_MS,
  });
}

export function useStoreRestaurant(restaurantId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.restaurant(restaurantId),
    queryFn: () => restaurantService.getStoreRestaurant(restaurantId),
    // "Open now" changes with the clock: not kept as long as the rest
    staleTime: 30_000,
  });
}

export function useRestaurantMenu(restaurantId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.menu(restaurantId),
    queryFn: () => catalogService.getRestaurantMenu(restaurantId),
    staleTime: STOREFRONT_STALE_MS,
  });
}

/** What a dish looks like on its restaurant's menu: its own picture (null: none uploaded) and its category. */
export interface MenuDishLook {
  imageUrl: string | null;
  categoryName: string;
}

/**
 * The menu pictures of the dishes of some restaurants, by menu item: their public menus, one request per restaurant,
 * shared with the menu pages. A dish no longer on a menu is missing from it (it then shows a plain tile); while the
 * menus load the map is empty.
 */
export function useMenuDishLooks(
  restaurantIds: readonly number[],
): ReadonlyMap<number, MenuDishLook> {
  const unique = [...new Set(restaurantIds)].sort((first, second) => first - second);
  const menus = useQueries({
    queries: unique.map((restaurantId) => ({
      queryKey: QUERY_KEYS.storefront.menu(restaurantId),
      queryFn: () => catalogService.getRestaurantMenu(restaurantId),
      staleTime: STOREFRONT_STALE_MS,
    })),
  });
  return new Map(
    menus.flatMap((menu) =>
      (menu.data?.sections ?? []).flatMap((section) =>
        section.items.map(
          (item) =>
            [item.id, { imageUrl: item.imageUrl, categoryName: section.categoryName }] as const,
        ),
      ),
    ),
  );
}

export function useStoreCategories() {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.categories,
    queryFn: () => catalogService.getStoreCategories(),
    staleTime: 10 * 60_000,
  });
}

export function useRestaurantSearch(
  params: RestaurantSearchParams,
  page: number,
  size: number,
  enabled = true,
) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.searchRestaurants(params, page),
    queryFn: () => searchService.searchRestaurants(params, page, size),
    placeholderData: keepPreviousData,
    staleTime: STOREFRONT_STALE_MS,
    enabled,
  });
}

export function useFoodSearch(
  params: FoodSearchParams,
  page: number,
  size: number,
  enabled = true,
) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.searchFoods(params, page, size),
    queryFn: () => searchService.searchFoods(params, page, size),
    placeholderData: keepPreviousData,
    staleTime: STOREFRONT_STALE_MS,
    enabled,
  });
}

/** Suggestions for the search box, from the second letter on (pass already debounced text). */
export function useSuggestions(text: string) {
  const query = text.trim();
  return useQuery({
    queryKey: QUERY_KEYS.storefront.suggest(query),
    queryFn: () => searchService.suggest(query),
    enabled: query.length >= 2 && query.length <= 50,
    staleTime: STOREFRONT_STALE_MS,
    placeholderData: keepPreviousData,
  });
}
