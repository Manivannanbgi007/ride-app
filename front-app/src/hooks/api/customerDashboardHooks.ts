import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { toApiError } from '@/api/apiErrorHandler';
import { useCustomerId } from '@/hooks/api/cartHooks';
import { useMyOrders } from '@/hooks/api/myOrderHooks';
import { orderService } from '@/services/orderService';
import type { OrderSummaryItem } from '@/types/order';
import { QUERY_KEYS } from '@/utils/queryKeys';
import { isFinishedOrder, MAX_QUANTITY_PER_DISH } from '@/utils/shoppingUtils';

/** The dashboard's figures change as orders move: they are asked for again now and then while it is open. */
const DASHBOARD_REFRESH_MS = 60_000;

/** The signed-in customer's orders in numbers, all time (the dashboard's cards). */
export function useMyOrderSummary() {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.orderSummary(customerId),
    queryFn: () => orderService.getMySummary(),
    enabled: customerId > 0,
    refetchInterval: DASHBOARD_REFRESH_MS,
  });
}

/**
 * What the signed-in customer ordered and spent in a period, day by day (the dashboard's charts). The previous
 * period stays on screen while another one loads.
 */
export function useMyOrderActivity(fromDate: string, toDate: string) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.orderActivity(customerId, fromDate, toDate),
    queryFn: () => orderService.getMyActivity(fromDate, toDate),
    enabled: customerId > 0,
    placeholderData: keepPreviousData,
    refetchInterval: DASHBOARD_REFRESH_MS,
  });
}

/** The dishes the signed-in customer orders most, with today's price and stock ("Order again"). */
export function useMyFavouriteDishes(limit: number) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.customer.favouriteDishes(customerId, limit),
    queryFn: () => orderService.getMyFavouriteDishes(limit),
    enabled: customerId > 0,
    staleTime: 5 * 60_000,
  });
}

/** How many of the most ordered dishes are asked for (the "frequent picks" row and the dishes "You may like" skips). */
export const FREQUENT_PICKS_LIMIT = 8;
/** A dish is a frequent pick once it was in this many of the customer's orders. */
export const FREQUENT_PICK_MIN_ORDERS = 2;

/**
 * The customer's frequent picks: the dishes that were in at least two of their orders, most orders first (then most
 * portions, then the latest; order-service ranks them, counting each order once and leaving out cancelled, declined
 * and unpaid orders). The list is ranked, so the ones ordered once all come after: keeping the first ones is enough.
 */
export function useFrequentPicks() {
  const favourites = useMyFavouriteDishes(FREQUENT_PICKS_LIMIT);
  const picks = (favourites.data ?? []).filter(
    (dish) => dish.timesOrdered >= FREQUENT_PICK_MIN_ORDERS,
  );
  return { ...favourites, picks };
}

/** How many of the latest orders the dashboard reads: enough for the orders on their way and four recent ones. */
export const DASHBOARD_ORDERS_PAGE = 8;

/**
 * The dashboard's orders, from one request (every section that needs them shares it): the newest first page, split
 * into the ones still going (placed, being prepared or delivered, or waiting for payment) and the finished ones.
 */
export function useDashboardOrders() {
  const orders = useMyOrders(undefined, 0, DASHBOARD_ORDERS_PAGE);
  const content = orders.data?.content ?? [];
  return {
    ...orders,
    active: content.filter((order) => !isFinishedOrder(order.status)),
    finished: content.filter((order) => isFinishedOrder(order.status)),
  };
}

/** What reordering did: the dishes now in the cart, and the ones that could not be added (with the reason). */
export interface ReorderResult {
  added: number;
  failed: { name: string; reason: string }[];
}

/**
 * "Reorder": the dishes of a past order go into the cart again, each with the quantity ordered then (at least what
 * is already in the cart, at most the cart's limit). The cart prices them at today's prices and refuses a dish that
 * cannot be ordered now; those are reported, the others stay. `replaceCart` empties a cart holding another
 * restaurant's dishes first (a cart holds one restaurant's dishes; the customer confirms that before).
 * One dish at a time: the cart is one record, and parallel changes would race.
 */
export function useReorder() {
  const customerId = useCustomerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      items,
      replaceCart,
    }: {
      items: readonly OrderSummaryItem[];
      replaceCart: boolean;
    }): Promise<ReorderResult> => {
      if (replaceCart) {
        await orderService.clearCart();
      }
      const result: ReorderResult = { added: 0, failed: [] };
      let inCart = replaceCart ? [] : ((await orderService.getCart()).items ?? []);
      for (const item of items) {
        const already = inCart.find((line) => line.menuItemId === item.menuItemId)?.quantity ?? 0;
        const quantity = Math.min(Math.max(item.quantity, already), MAX_QUANTITY_PER_DISH);
        try {
          inCart = (await orderService.setCartQuantity(item.menuItemId, quantity)).items;
          result.added += 1;
        } catch (error) {
          result.failed.push({ name: item.name, reason: toApiError(error).userMessage });
        }
      }
      return result;
    },
    // Whatever happened, the cart shown must be the server's
    onSettled: () =>
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.customer.cart(customerId) }),
  });
}
