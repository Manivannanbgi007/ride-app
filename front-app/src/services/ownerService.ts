import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type { MenuItem } from '@/types/catalog';
import type { OrderDetails, OrderSummary } from '@/types/order';
import type {
  OperatingHoursUpdate,
  OwnerMenuItemRequest,
  OwnerMenuItemUpdate,
  OwnerOrderFilters,
  OwnerRestaurantRequest,
  OwnerRestaurantUpdate,
  SalesSummary,
  TopItems,
} from '@/types/owner';
import type { RestaurantDetails, RestaurantSummary } from '@/types/restaurant';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

const API = API_ENDPOINTS.owner;

/**
 * The restaurant owner's APIs: their own restaurants, the orders of each, and its menu. Every call names the
 * restaurant; the services answer 403/404 for one that is not the caller's, whatever the page asks.
 */
export const ownerService = {
  /** All the owner's restaurants, whatever their status. */
  getRestaurants: () => apiClient.get<RestaurantSummary[]>(API.restaurants),

  /** Registers a new restaurant; it starts as PENDING until the platform team approves it. */
  registerRestaurant: (request: OwnerRestaurantRequest) =>
    apiClient.post<RestaurantDetails>(API.restaurants, request),

  getRestaurant: (restaurantId: number) =>
    apiClient.get<RestaurantDetails>(API.restaurant(restaurantId)),

  /** Changes the restaurant as a whole; 409 when it changed since `version` was read. */
  updateRestaurant: (restaurantId: number, update: OwnerRestaurantUpdate) =>
    apiClient.put<RestaurantDetails>(API.restaurant(restaurantId), update),

  /** Replaces the weekly opening hours. */
  updateOperatingHours: (restaurantId: number, update: OperatingHoursUpdate) =>
    apiClient.put<RestaurantDetails>(API.operatingHours(restaurantId), update),

  /** Sends a rejected restaurant to the admins for review again. */
  resubmitRestaurant: (restaurantId: number) =>
    apiClient.put<RestaurantDetails>(API.resubmit(restaurantId)),

  /** One page of the restaurant's orders (page 0 = the first). */
  getOrders: (restaurantId: number, filters: OwnerOrderFilters, page: number, size: number) =>
    apiClient.get<PageResponse<OrderSummary>>(API.orders(restaurantId), {
      params: { ...filters, page, size },
    }),

  /** How many orders match the filters: the list's own total, asked for with a one-row page (no count API). */
  countOrders: async (restaurantId: number, filters: OwnerOrderFilters): Promise<number> => {
    const onePage = await apiClient.get<PageResponse<OrderSummary>>(API.orders(restaurantId), {
      params: { ...filters, page: 0, size: 1 },
    });
    return onePage.totalElements;
  },

  getOrder: (restaurantId: number, orderId: number) =>
    apiClient.get<OrderDetails>(API.order(restaurantId, orderId)),

  /** PLACED -> ACCEPTED */
  acceptOrder: (restaurantId: number, orderId: number) =>
    apiClient.put<OrderDetails>(API.orderAction(restaurantId, orderId, 'accept')),

  /** PLACED -> REJECTED; the reason is shown to the customer. */
  rejectOrder: (restaurantId: number, orderId: number, reason: string) =>
    apiClient.put<OrderDetails>(API.orderAction(restaurantId, orderId, 'reject'), { reason }),

  /** ACCEPTED -> PREPARING */
  startPreparing: (restaurantId: number, orderId: number) =>
    apiClient.put<OrderDetails>(API.orderAction(restaurantId, orderId, 'preparing')),

  /** PREPARING -> READY_FOR_PICKUP (a delivery partner is then offered the order) */
  markReady: (restaurantId: number, orderId: number) =>
    apiClient.put<OrderDetails>(API.orderAction(restaurantId, orderId, 'ready')),

  /** The orders and money of a period; days are 'YYYY-MM-DD', inclusive (none: today). */
  getSalesSummary: (restaurantId: number, fromDate?: string, toDate?: string) =>
    apiClient.get<SalesSummary>(API.salesSummary(restaurantId), {
      params: { fromDate, toDate },
    }),

  /** The most ordered dishes of a period (the API names the days `from` and `to`). */
  getTopItems: (restaurantId: number, fromDate?: string, toDate?: string) =>
    apiClient.get<TopItems>(API.topItems(restaurantId), {
      params: { from: fromDate, to: toDate },
    }),

  /** Every dish of the restaurant, whatever its status. */
  getMenuItems: (restaurantId: number) => apiClient.get<MenuItem[]>(API.menuItems(restaurantId)),

  createMenuItem: (restaurantId: number, item: OwnerMenuItemRequest) =>
    apiClient.post<MenuItem>(API.menuItems(restaurantId), item),

  /** Changes a dish as a whole; 409 when it changed since `version` was read. */
  updateMenuItem: (restaurantId: number, itemId: number, item: OwnerMenuItemUpdate) =>
    apiClient.put<MenuItem>(API.menuItem(restaurantId, itemId), item),

  updatePrice: (restaurantId: number, itemId: number, price: number, version: number) =>
    apiClient.put<MenuItem>(API.menuItemAction(restaurantId, itemId, 'price'), { price, version }),

  /** false = sold out for now (the dish stays on the menu); true = back in stock. */
  setAvailability: (restaurantId: number, itemId: number, available: boolean) =>
    apiClient.put<MenuItem>(API.menuItemAction(restaurantId, itemId, 'availability'), {
      available,
    }),

  /** Shows a hidden dish on the menu again. */
  enableMenuItem: (restaurantId: number, itemId: number) =>
    apiClient.put<MenuItem>(API.menuItemAction(restaurantId, itemId, 'enable')),

  /** Hides a dish from the menu. */
  disableMenuItem: (restaurantId: number, itemId: number) =>
    apiClient.put<MenuItem>(API.menuItemAction(restaurantId, itemId, 'disable')),
};
