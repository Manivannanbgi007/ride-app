import {
  keepPreviousData,
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';

import { ownerService } from '@/services/ownerService';
import { useAppSelector } from '@/store/hooks';
import { selectOwner } from '@/store/slices/sessionSlice';
import type {
  OperatingHoursUpdate,
  OwnerMenuItemRequest,
  OwnerMenuItemUpdate,
  OwnerOrderAction,
  OwnerOrderFilters,
  OwnerRestaurantRequest,
  OwnerRestaurantUpdate,
} from '@/types/owner';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** How often the order screens ask again: new orders arrive while the owner looks at the page. */
export const ORDERS_REFRESH_MS = 20_000;

/** The signed-in owner's id (0 when nobody is signed in: queries that need it stay switched off). */
export function useOwnerId(): number {
  return useAppSelector(selectOwner)?.id ?? 0;
}

/** The owner's restaurants, whatever their status. */
export function useMyRestaurants() {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.restaurants(ownerId),
    queryFn: () => ownerService.getRestaurants(),
    enabled: ownerId > 0,
  });
}

/** One restaurant of the owner, with everything (address, hours, documents, version). */
export function useOwnerRestaurantDetails(restaurantId: number) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.restaurant(ownerId, restaurantId),
    queryFn: () => ownerService.getRestaurant(restaurantId),
    enabled: ownerId > 0 && restaurantId > 0,
  });
}

/** After a change of the restaurant: its details and the list (the name and status in the header) reload. */
function useRefreshRestaurants() {
  const ownerId = useOwnerId();
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: QUERY_KEYS.owner.restaurants(ownerId) });
}

/** Registering a restaurant: the list (and the header) reload to show it. */
export function useRegisterRestaurant() {
  const refresh = useRefreshRestaurants();
  return useMutation({
    mutationFn: (request: OwnerRestaurantRequest) => ownerService.registerRestaurant(request),
    onSuccess: () => refresh(),
  });
}

export function useUpdateRestaurant(restaurantId: number) {
  const refresh = useRefreshRestaurants();
  return useMutation({
    mutationFn: (update: OwnerRestaurantUpdate) =>
      ownerService.updateRestaurant(restaurantId, update),
    onSuccess: () => refresh(),
  });
}

export function useUpdateOperatingHours(restaurantId: number) {
  const refresh = useRefreshRestaurants();
  return useMutation({
    mutationFn: (update: OperatingHoursUpdate) =>
      ownerService.updateOperatingHours(restaurantId, update),
    onSuccess: () => refresh(),
  });
}

export function useResubmitRestaurant(restaurantId: number) {
  const refresh = useRefreshRestaurants();
  return useMutation({
    mutationFn: () => ownerService.resubmitRestaurant(restaurantId),
    onSuccess: () => refresh(),
  });
}

/**
 * One page of the restaurant's orders. With `live`, it asks again every few seconds (the previous page stays on
 * screen meanwhile), so a new order shows up without reloading.
 */
export function useOwnerOrders(
  restaurantId: number,
  filters: OwnerOrderFilters,
  page: number,
  size: number,
  live = false,
) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.orders(ownerId, restaurantId, filters, page, size),
    queryFn: () => ownerService.getOrders(restaurantId, filters, page, size),
    enabled: ownerId > 0 && restaurantId > 0,
    placeholderData: keepPreviousData,
    refetchInterval: live ? ORDERS_REFRESH_MS : false,
  });
}

/**
 * How many orders each set of filters matches (e.g. one per status tab), keyed like `counts`: asked again as often
 * as the lists, and after every order action (the keys sit under ordersAll). A count still loading is undefined.
 */
export function useOwnerOrderCounts<K extends string>(
  restaurantId: number,
  counts: readonly { key: K; filters: OwnerOrderFilters }[],
  enabled = true,
): Partial<Record<K, number>> {
  const ownerId = useOwnerId();
  return useQueries({
    queries: counts.map(({ filters }) => ({
      queryKey: QUERY_KEYS.owner.orderCount(ownerId, restaurantId, filters),
      queryFn: () => ownerService.countOrders(restaurantId, filters),
      enabled: enabled && ownerId > 0 && restaurantId > 0,
      placeholderData: keepPreviousData,
      refetchInterval: ORDERS_REFRESH_MS,
    })),
    combine: (results) => {
      const byKey: Partial<Record<K, number>> = {};
      results.forEach((result, index) => {
        if (result.data !== undefined) {
          byKey[counts[index].key] = result.data;
        }
      });
      return byKey;
    },
  });
}

/** One order with its timeline; asks again every few seconds while `live`. */
export function useOwnerOrder(restaurantId: number, orderId: number, live = false) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.order(ownerId, restaurantId, orderId),
    queryFn: () => ownerService.getOrder(restaurantId, orderId),
    enabled: ownerId > 0 && restaurantId > 0 && orderId > 0,
    refetchInterval: live ? ORDERS_REFRESH_MS : false,
  });
}

interface OrderActionInput {
  orderId: number;
  action: OwnerOrderAction;
  /** Required for 'reject': shown to the customer */
  reason?: string;
}

/** Accept, reject, start preparing, mark ready: the order is shown as it is now, and every list and total reloads. */
export function useOwnerOrderAction(restaurantId: number) {
  const ownerId = useOwnerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, action, reason }: OrderActionInput) => {
      switch (action) {
        case 'accept':
          return ownerService.acceptOrder(restaurantId, orderId);
        case 'reject':
          return ownerService.rejectOrder(restaurantId, orderId, reason ?? '');
        case 'preparing':
          return ownerService.startPreparing(restaurantId, orderId);
        default:
          return ownerService.markReady(restaurantId, orderId);
      }
    },
    onSuccess: async (order) => {
      queryClient.setQueryData(QUERY_KEYS.owner.order(ownerId, restaurantId, order.id), order);
      await queryClient.invalidateQueries({
        queryKey: QUERY_KEYS.owner.ordersAll(ownerId, restaurantId),
      });
    },
  });
}

/**
 * The orders and money of a period (the days are 'YYYY-MM-DD'; none: today). When other days are asked for, the
 * figures of the days before stay on screen (isPlaceholderData) until the new ones arrive.
 */
export function useSalesSummary(
  restaurantId: number,
  fromDate: string,
  toDate: string,
  live = false,
) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.sales(ownerId, restaurantId, fromDate, toDate),
    queryFn: () => ownerService.getSalesSummary(restaurantId, fromDate, toDate),
    enabled: ownerId > 0 && restaurantId > 0,
    placeholderData: keepPreviousData,
    refetchInterval: live ? ORDERS_REFRESH_MS : false,
  });
}

/** The most ordered dishes of a period; the dishes of the days before stay on screen while others load. */
export function useTopItems(restaurantId: number, fromDate: string, toDate: string) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.topItems(ownerId, restaurantId, fromDate, toDate),
    queryFn: () => ownerService.getTopItems(restaurantId, fromDate, toDate),
    enabled: ownerId > 0 && restaurantId > 0,
    placeholderData: keepPreviousData,
  });
}

/** Every dish of the restaurant, whatever its status. */
export function useOwnerMenu(restaurantId: number) {
  const ownerId = useOwnerId();
  return useQuery({
    queryKey: QUERY_KEYS.owner.menu(ownerId, restaurantId),
    queryFn: () => ownerService.getMenuItems(restaurantId),
    enabled: ownerId > 0 && restaurantId > 0,
  });
}

/** After a change of the menu the owner's list reloads (customers' menus reload on their own schedule). */
function useRefreshMenu(restaurantId: number) {
  const ownerId = useOwnerId();
  const queryClient = useQueryClient();
  return () =>
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.owner.menu(ownerId, restaurantId) });
}

export function useCreateMenuItem(restaurantId: number) {
  const refresh = useRefreshMenu(restaurantId);
  return useMutation({
    mutationFn: (item: OwnerMenuItemRequest) => ownerService.createMenuItem(restaurantId, item),
    onSuccess: () => refresh(),
  });
}

export function useUpdateMenuItem(restaurantId: number) {
  const refresh = useRefreshMenu(restaurantId);
  return useMutation({
    mutationFn: ({ itemId, item }: { itemId: number; item: OwnerMenuItemUpdate }) =>
      ownerService.updateMenuItem(restaurantId, itemId, item),
    onSuccess: () => refresh(),
  });
}

export function useUpdatePrice(restaurantId: number) {
  const refresh = useRefreshMenu(restaurantId);
  return useMutation({
    mutationFn: ({ itemId, price, version }: { itemId: number; price: number; version: number }) =>
      ownerService.updatePrice(restaurantId, itemId, price, version),
    onSuccess: () => refresh(),
  });
}

/** Sold out for now (available = false) or back in stock; the dish stays on the menu either way. */
export function useSetAvailability(restaurantId: number) {
  const refresh = useRefreshMenu(restaurantId);
  return useMutation({
    mutationFn: ({ itemId, available }: { itemId: number; available: boolean }) =>
      ownerService.setAvailability(restaurantId, itemId, available),
    onSuccess: () => refresh(),
  });
}

/** Hide a dish from the menu (visible: false) or show it again (true). */
export function useSetMenuItemVisible(restaurantId: number) {
  const refresh = useRefreshMenu(restaurantId);
  return useMutation({
    mutationFn: ({ itemId, visible }: { itemId: number; visible: boolean }) =>
      visible
        ? ownerService.enableMenuItem(restaurantId, itemId)
        : ownerService.disableMenuItem(restaurantId, itemId),
    onSuccess: () => refresh(),
  });
}
