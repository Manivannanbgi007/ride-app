import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  AdminRestaurantDetails,
  RestaurantAction,
  RestaurantDetails,
  RestaurantFilters,
  RestaurantSummary,
} from '@/types/restaurant';
import type {
  StoreRestaurant,
  StoreRestaurantFilters,
  StoreRestaurantSummary,
} from '@/types/storefront';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** restaurant-service APIs: the admin's, and the public restaurant list and details. */
export const restaurantService = {
  /** Public: restaurants customers can order from (ACTIVE only), with whether each is open now. */
  browseRestaurants: (filters: StoreRestaurantFilters, page: number, size: number) =>
    apiClient.get<PageResponse<StoreRestaurantSummary>>(API_ENDPOINTS.storefront.restaurants, {
      params: { ...filters, page, size },
    }),

  /** Public: one restaurant with its address and opening hours (404 when it is not taking orders). */
  getStoreRestaurant: (restaurantId: number) =>
    apiClient.get<StoreRestaurant>(API_ENDPOINTS.storefront.restaurant(restaurantId)),

  /** Restaurants matching the filters, one page (page 0 = the first). */
  searchRestaurants: (filters: RestaurantFilters, page: number, size: number) =>
    apiClient.get<PageResponse<RestaurantSummary>>(API_ENDPOINTS.admin.restaurants.list, {
      params: { ...filters, page, size },
    }),

  /** The restaurant and its owner. */
  getRestaurant: (restaurantId: number) =>
    apiClient.get<AdminRestaurantDetails>(API_ENDPOINTS.admin.restaurants.details(restaurantId)),

  /**
   * approve / reject (a PENDING restaurant), activate, deactivate, suspend. Audited; the reason is shown to the
   * owner.
   */
  changeStatus: (restaurantId: number, action: RestaurantAction, reason: string) =>
    apiClient.put<RestaurantDetails>(API_ENDPOINTS.admin.restaurants.action(restaurantId, action), {
      reason,
    }),

  getAuditLogs: (restaurantId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(
      API_ENDPOINTS.admin.restaurants.auditLogs(restaurantId),
      { params: { size: 50 } },
    ),
};
