import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  CustomerOrderActivity,
  CustomerOrderSummary,
  CustomerTopFoods,
  FavouriteDish,
  DishRevenue,
  DishRevenuePeriod,
  OrderDetails,
  OrderFilters,
  OrderStatus,
  OrderSummary,
} from '@/types/order';
import type { Cart, PlaceOrderRequest } from '@/types/shopping';
import { API_ENDPOINTS, IDEMPOTENCY_KEY_HEADER } from '@/utils/apiEndpoints';

/** order-service APIs: the admin's, and the signed-in customer's cart and orders. */
export const orderService = {
  getCart: () => apiClient.get<Cart>(API_ENDPOINTS.cart.cart),

  /**
   * Sets how many of a dish are in the cart (1 to 20), adding it when needed. 409 when the cart holds another
   * restaurant's dishes: the customer clears the cart first.
   */
  setCartQuantity: (menuItemId: number, quantity: number) =>
    apiClient.put<Cart>(API_ENDPOINTS.cart.item(menuItemId), { quantity }),

  removeFromCart: (menuItemId: number) =>
    apiClient.delete<Cart>(API_ENDPOINTS.cart.item(menuItemId)),

  /** Empties the cart (and removes its coupon); never fails. */
  clearCart: () => apiClient.delete(API_ENDPOINTS.cart.cart),

  /** Applies a coupon to the cart (422 with the reason when it cannot be used: too small an order, expired, ...). */
  applyCoupon: (code: string) => apiClient.put<Cart>(API_ENDPOINTS.cart.coupon, { code }),

  removeCoupon: () => apiClient.delete<Cart>(API_ENDPOINTS.cart.coupon),

  /**
   * Checkout: the cart (with its coupon) becomes an order and the cart is emptied. Online payments start as
   * PAYMENT_PENDING (paid next), cash on delivery as PLACED. The key makes a retry safe: the same key and request
   * return the order already created instead of a second one.
   */
  placeOrder: (request: PlaceOrderRequest, idempotencyKey: string) =>
    apiClient.post<OrderDetails>(API_ENDPOINTS.orders.list, request, {
      headers: { [IDEMPOTENCY_KEY_HEADER]: idempotencyKey },
    }),

  /** The signed-in customer's orders, newest first (optionally of one status). */
  getMyOrders: (status: OrderStatus | undefined, page: number, size: number) =>
    apiClient.get<PageResponse<OrderSummary>>(API_ENDPOINTS.orders.list, {
      params: { status, page, size, sort: 'createdAt,desc' },
    }),

  /**
   * The customer cancels before the restaurant accepts (409 afterwards). What was paid online is refunded by
   * itself; the coupon can be used again.
   */
  cancelMyOrder: (orderId: number, reason: string) =>
    apiClient.put<OrderDetails>(API_ENDPOINTS.orders.cancel(orderId), { reason }),

  /** The signed-in customer's orders in numbers, all time (their dashboard's cards). */
  getMySummary: () => apiClient.get<CustomerOrderSummary>(API_ENDPOINTS.orders.summary),

  /** What the signed-in customer ordered and spent from one day to another ('YYYY-MM-DD'), day by day. */
  getMyActivity: (fromDate: string, toDate: string) =>
    apiClient.get<CustomerOrderActivity>(API_ENDPOINTS.orders.activity, {
      params: { fromDate, toDate },
    }),

  /** The dishes the signed-in customer orders most, with today's price and stock (503 when not confirmed). */
  getMyFavouriteDishes: (limit: number) =>
    apiClient.get<FavouriteDish[]>(API_ENDPOINTS.orders.favouriteDishes, { params: { limit } }),

  /** One of the signed-in customer's orders, with its timeline (404 for anybody else's). */
  getMyOrder: (orderId: number) =>
    apiClient.get<OrderDetails>(API_ENDPOINTS.orders.details(orderId)),

  /** Orders matching the filters, newest first, one page (page 0 = the first). */
  searchOrders: (filters: OrderFilters, page: number, size: number) =>
    apiClient.get<PageResponse<OrderSummary>>(API_ENDPOINTS.admin.orders.list, {
      params: { ...filters, page, size, sort: 'createdAt,desc' },
    }),

  getOrder: (orderId: number) =>
    apiClient.get<OrderDetails>(API_ENDPOINTS.admin.orders.details(orderId)),

  /** Admin intervention (audited): the customer is told, and refunded if they paid online. */
  cancelOrder: (orderId: number, reason: string) =>
    apiClient.put<OrderDetails>(API_ENDPOINTS.admin.orders.cancel(orderId), { reason }),

  getAuditLogs: (orderId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.orders.auditLogs(orderId), {
      params: { size: 50 },
    }),

  /** A customer's orders in numbers (for the customer screen). */
  getCustomerSummary: (customerId: number) =>
    apiClient.get<CustomerOrderSummary>(API_ENDPOINTS.admin.orders.customerSummary(customerId)),

  /** A dish's revenue from delivered orders: the last 12 months, 12 weeks or 30 days (empty ones included). */
  getDishRevenue: (menuItemId: number, period: DishRevenuePeriod) =>
    apiClient.get<DishRevenue>(API_ENDPOINTS.admin.orders.dishRevenue(menuItemId), {
      params: { period },
    }),

  /** The dishes a customer orders most, by total spent (delivered orders). */
  getCustomerTopFoods: (customerId: number, period: DishRevenuePeriod, limit: number) =>
    apiClient.get<CustomerTopFoods>(API_ENDPOINTS.admin.orders.customerTopFoods(customerId), {
      params: { period, limit },
    }),
};
