import { apiClient } from '@/api/apiClient';
import type {
  FacetValue,
  FoodHit,
  FoodSearchParams,
  RestaurantHit,
  RestaurantSearchParams,
  SearchResults,
  Suggestions,
} from '@/types/storefront';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** search-service (Elasticsearch, public): forgives typos and matches the start of words. */
export const searchService = {
  searchRestaurants: (params: RestaurantSearchParams, page: number, size: number) =>
    apiClient.get<SearchResults<RestaurantHit, { cuisines: FacetValue[] }>>(
      API_ENDPOINTS.search.restaurants,
      { params: { ...params, page, size } },
    ),

  searchFoods: (params: FoodSearchParams, page: number, size: number) =>
    apiClient.get<SearchResults<FoodHit, { foodTypes: FacetValue[]; categories: FacetValue[] }>>(
      API_ENDPOINTS.search.foods,
      { params: { ...params, page, size } },
    ),

  /** Up to 5 restaurants, dishes and categories for the search box (text: 1 to 50 characters). */
  suggest: (text: string) =>
    apiClient.get<Suggestions>(API_ENDPOINTS.search.suggest, { params: { q: text } }),
};
