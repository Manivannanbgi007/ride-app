import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { ratingService } from '@/services/ratingService';
import type { PublicReviewSort, RatingLookup, RatingSummary } from '@/types/rating';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** review-service answers for at most 100 ids at a time; a page of cards never has more. */
const MAX_IDS = 100;

/** The same ids in any order are one request: sorted, without duplicates. */
function distinctIds(ids: readonly number[]): number[] {
  return [...new Set(ids)].sort((a, b) => a - b).slice(0, MAX_IDS);
}

function toLookup(summaries: RatingSummary[]): RatingLookup {
  return Object.fromEntries(summaries.map((summary) => [summary.id, summary]));
}

/**
 * The ratings of the restaurants on a page, by restaurant id. A rating is a decoration: while it loads, or if it
 * cannot be loaded, there is no lookup (undefined) and the page shows no stars instead of an error. A restaurant
 * missing from a loaded lookup has no ratings yet.
 */
export function useRestaurantRatings(ids: readonly number[]): RatingLookup | undefined {
  const wanted = distinctIds(ids);
  const query = useQuery({
    queryKey: QUERY_KEYS.ratings.restaurants(wanted),
    queryFn: async () => toLookup(await ratingService.getRestaurantRatings(wanted)),
    enabled: wanted.length > 0,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
  // The previous page's ratings do not describe this page's ids
  return query.isPlaceholderData ? undefined : query.data;
}

/** The ratings of the dishes on a page, by dish id (the same rules as for restaurants). */
export function useDishRatings(ids: readonly number[]): RatingLookup | undefined {
  const wanted = distinctIds(ids);
  const query = useQuery({
    queryKey: QUERY_KEYS.ratings.dishes(wanted),
    queryFn: async () => toLookup(await ratingService.getDishRatings(wanted)),
    enabled: wanted.length > 0,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
  // The previous page's ratings do not describe this page's ids
  return query.isPlaceholderData ? undefined : query.data;
}

/** A restaurant's average, count and 1 to 5 star distribution (its page). */
export function useRestaurantRating(restaurantId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.ratings.restaurant(restaurantId),
    queryFn: () => ratingService.getRestaurantRating(restaurantId),
    enabled: restaurantId > 0,
    staleTime: 30_000,
  });
}

/** A dish's average, count and 1 to 5 star distribution (its reviews' window). */
export function useDishRating(menuItemId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.ratings.dish(menuItemId),
    queryFn: () => ratingService.getDishRating(menuItemId),
    enabled: menuItemId > 0,
    staleTime: 30_000,
  });
}

/** One page of the reviews that rated a dish; the previous page stays on screen while the next loads. */
export function useDishReviews(menuItemId: number, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.ratings.dishReviews(menuItemId, page),
    queryFn: () => ratingService.getDishReviews(menuItemId, page, size),
    enabled: menuItemId > 0,
    placeholderData: keepPreviousData,
  });
}

/** One page of a restaurant's reviews; the previous page stays on screen while the next loads. */
export function useRestaurantReviews(
  restaurantId: number,
  sort: PublicReviewSort,
  page: number,
  size: number,
) {
  return useQuery({
    queryKey: QUERY_KEYS.ratings.restaurantReviews(restaurantId, sort, page),
    queryFn: () => ratingService.getRestaurantReviews(restaurantId, sort, page, size),
    enabled: restaurantId > 0,
    placeholderData: keepPreviousData,
  });
}
