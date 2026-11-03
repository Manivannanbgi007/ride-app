import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { restaurantService } from '@/services/restaurantService';
import type {
  AdminRestaurantDetails,
  RestaurantAction,
  RestaurantFilters,
} from '@/types/restaurant';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** One page of restaurants. While the next page loads, the current one stays on screen. */
export function useRestaurants(filters: RestaurantFilters, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.restaurants.list(filters, page, size),
    queryFn: () => restaurantService.searchRestaurants(filters, page, size),
    placeholderData: keepPreviousData,
  });
}

/** A restaurant and its owner; not asked for while the id is not known yet (e.g. a dish still loading). */
export function useRestaurant(restaurantId: number | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.restaurants.details(restaurantId ?? 0),
    queryFn: () => restaurantService.getRestaurant(restaurantId ?? 0),
    enabled: restaurantId !== undefined,
  });
}

export function useRestaurantAuditLogs(restaurantId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.restaurants.auditLogs(restaurantId),
    queryFn: () => restaurantService.getAuditLogs(restaurantId),
  });
}

/** approve, reject, activate, deactivate or suspend; then the lists, history and dashboard are reloaded. */
export function useChangeRestaurantStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      restaurantId,
      action,
      reason,
    }: {
      restaurantId: number;
      action: RestaurantAction;
      reason: string;
    }) => restaurantService.changeStatus(restaurantId, action, reason),
    onSuccess: async (restaurant) => {
      // The answer is the restaurant without its owner: keep the owner already shown
      queryClient.setQueryData<AdminRestaurantDetails>(
        QUERY_KEYS.restaurants.details(restaurant.id),
        (current) => (current ? { ...current, restaurant } : current),
      );
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.restaurants.all }),
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.dashboardAll }),
      ]);
    },
  });
}
