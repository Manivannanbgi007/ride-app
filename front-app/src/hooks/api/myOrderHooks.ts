import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useCustomerId } from '@/hooks/api/cartHooks';
import { deliveryService } from '@/services/deliveryService';
import { liveTrackingService } from '@/services/liveTrackingService';
import { useLiveStatus } from '@/realtime/liveStatusContext';
import { orderService } from '@/services/orderService';
import type { OrderDetails, OrderStatus } from '@/types/order';
import { QUERY_KEYS } from '@/utils/queryKeys';
import {
  deliveryStatusProgress,
  forwardOnly,
  livePhaseProgress,
  orderStatusProgress,
} from '@/utils/statusProgressUtils';

/** How often to ask for an order again: milliseconds, false = not, or decided from the order itself. */
export type OrderRefresh = number | false | ((order: OrderDetails | undefined) => number | false);

/**
 * One of the signed-in customer's orders. `refresh` keeps it fresh while something is expected to change (the
 * payment being confirmed, the restaurant accepting).
 */
export function useMyOrder(orderId: number, refresh: OrderRefresh = false) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.order(customerId, orderId),
    queryFn: () => orderService.getMyOrder(orderId),
    enabled: customerId > 0,
    refetchInterval: (query) =>
      typeof refresh === 'function' ? refresh(query.state.data) : refresh,
    // A late answer (overtaken by a newer one) never takes the order back to an earlier status
    structuralSharing: forwardOnly(orderStatusProgress, 'order status'),
  });
}

/** While an order is on its way, it (and its delivery) is asked for again every 15 seconds... */
export const ORDER_ACTIVE_REFRESH_MS = 15_000;
/** ...or every 2 minutes while the live connection is open (changes then arrive by themselves). */
export const ORDER_LIVE_SAFETY_REFRESH_MS = 120_000;

/** How often an order still on its way is asked for again: rarely while live updates tell about changes. */
export function useOrderRefreshMs(): number {
  return useLiveStatus() === 'open' ? ORDER_LIVE_SAFETY_REFRESH_MS : ORDER_ACTIVE_REFRESH_MS;
}

/** The customer's orders, newest first; refreshed every minute while the list is open. */
export function useMyOrders(status: OrderStatus | undefined, page: number, size: number) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.orders(customerId, status, page, size),
    queryFn: () => orderService.getMyOrders(status, page, size),
    enabled: customerId > 0,
    placeholderData: keepPreviousData,
    refetchInterval: 60_000,
  });
}

/** Cancelling changes the order (shown at once) and the list. */
export function useCancelMyOrder() {
  const customerId = useCustomerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, reason }: { orderId: number; reason: string }) =>
      orderService.cancelMyOrder(orderId, reason),
    onSuccess: async (order) => {
      queryClient.setQueryData(QUERY_KEYS.customer.order(customerId, order.id), order);
      await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.customer.allOrders(customerId) });
    },
  });
}

/**
 * The delivery of one of the customer's orders, once the food is ready (`enabled`), refreshed while it is on
 * its way.
 */
export function useOrderTracking(
  orderId: number,
  enabled: boolean,
  refreshEveryMs: number | false,
) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.tracking(customerId, orderId),
    queryFn: () => deliveryService.getOrderTracking(orderId),
    enabled: enabled && customerId > 0,
    refetchInterval: refreshEveryMs,
    // Picked up or delivered is never undone by a late answer
    structuralSharing: forwardOnly(deliveryStatusProgress, 'delivery status'),
  });
}

/** How often the live map asks where the partner is (a phone sends its position about this often) */
export const LIVE_TRACKING_REFRESH_MS = 3_000;

/**
 * Where the partner bringing the order is right now (the live map), asked for again every few seconds until the
 * food is delivered. `enabled` = false for an order that will not be delivered (cancelled or rejected).
 */
export function useLiveTracking(orderId: number, enabled = true) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.liveTracking(customerId, orderId),
    queryFn: () => liveTrackingService.getLiveTracking(orderId),
    enabled: enabled && customerId > 0,
    refetchInterval: (query) =>
      query.state.data?.phase === 'DELIVERED' ? false : LIVE_TRACKING_REFRESH_MS,
    // A tab in the background has nobody watching the map
    refetchIntervalInBackground: false,
    // A late answer never moves the partner back to before the pickup, nor undoes "delivered"
    structuralSharing: forwardOnly(livePhaseProgress, 'live tracking phase'),
  });
}
