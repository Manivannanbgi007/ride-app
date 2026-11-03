import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { orderService } from '@/services/orderService';
import type { DishRevenuePeriod, OrderFilters } from '@/types/order';
import { QUERY_KEYS } from '@/utils/queryKeys';

/**
 * One page of orders. While the next page loads, the current one stays on screen.
 * enabled = false: not asked for (yet), e.g. while a filter is invalid.
 */
export function useOrders(filters: OrderFilters, page: number, size: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.list(filters, page, size),
    queryFn: () => orderService.searchOrders(filters, page, size),
    placeholderData: keepPreviousData,
    enabled,
  });
}

/** enabled = false: not asked for (yet), e.g. a history that is not open */
export function useOrder(orderId: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.details(orderId),
    queryFn: () => orderService.getOrder(orderId),
    enabled,
  });
}

export function useOrderAuditLogs(orderId: number, enabled = true) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.auditLogs(orderId),
    queryFn: () => orderService.getAuditLogs(orderId),
    enabled,
  });
}

export function useCustomerOrderSummary(customerId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.customerSummary(customerId),
    queryFn: () => orderService.getCustomerSummary(customerId),
  });
}

/** Cancels an order; afterwards every order list, the order, its history and the dashboard are reloaded. */
export function useCancelOrder() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ orderId, reason }: { orderId: number; reason: string }) =>
      orderService.cancelOrder(orderId, reason),
    onSuccess: async (order) => {
      queryClient.setQueryData(QUERY_KEYS.orders.details(order.id), order);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.orders.all }),
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.dashboardAll }),
      ]);
    },
  });
}

/** A dish's revenue per month, week or day; each period is kept apart, so switching back is instant. */
export function useDishRevenue(menuItemId: number, period: DishRevenuePeriod) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.dishRevenue(menuItemId, period),
    queryFn: () => orderService.getDishRevenue(menuItemId, period),
  });
}

/** The dishes a customer orders most in the last month, week or today; each period is kept apart. */
export function useCustomerTopFoods(customerId: number, period: DishRevenuePeriod, limit: number) {
  return useQuery({
    queryKey: QUERY_KEYS.orders.customerTopFoods(customerId, period, limit),
    queryFn: () => orderService.getCustomerTopFoods(customerId, period, limit),
  });
}
