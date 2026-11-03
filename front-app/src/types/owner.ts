import type { MenuItemCreateRequest } from '@/types/catalog';
import type { DayPoint } from '@/types/dashboard';
import type { OrderStatus } from '@/types/order';
import type { DayOfWeek, RestaurantAddress } from '@/types/restaurant';

/** GET /api/v1/restaurants/mine/{id}/orders/sales-summary: the orders and money of a period (default: today). */
export interface SalesSummary {
  restaurantId: number;
  /** 'YYYY-MM-DD' */
  fromDate: string;
  toDate: string;
  totalOrders: number;
  /** How many orders are in each status; statuses nobody is in are missing */
  ordersByStatus: Partial<Record<OrderStatus, number>>;
  acceptedOrdersValue: number;
  deliveredOrdersValue: number;
  currency: string;
  /** Each day of the period: orders placed, delivered, cancelled or rejected, and sales (absent from older answers) */
  perDay?: DayPoint[];
  /** Each hour of the day (0-23), summed over the period's days: the same figures (absent from older answers) */
  perHour?: HourPoint[];
}

/** One hour of the day (13 = 1 pm to 2 pm): orders placed in it, delivered, cancelled or rejected, and sales. */
export interface HourPoint {
  hour: number;
  orders: number;
  completed: number;
  cancelled: number;
  revenue: number;
}

export interface TopItem {
  menuItemId: number;
  name: string;
  quantity: number;
  orders: number;
  revenue: number;
}

/** GET /api/v1/restaurants/mine/{id}/orders/top-items: the most ordered dishes of a period. */
export interface TopItems {
  restaurantId: number;
  from: string;
  to: string;
  items: TopItem[];
}

/** The filters of the owner's order list (all optional; the dates are inclusive days, 'YYYY-MM-DD'). */
export interface OwnerOrderFilters {
  status?: OrderStatus;
  fromDate?: string;
  toDate?: string;
  /** 'createdAt,desc' (default) or 'createdAt,asc' (oldest first: the orders waiting longest) */
  sort?: string;
}

/** What the owner does with an order: the next step of the kitchen. */
export type OwnerOrderAction = 'accept' | 'reject' | 'preparing' | 'ready';

/** A menu item as the owner writes it (the restaurant is in the address). */
export type OwnerMenuItemRequest = Omit<MenuItemCreateRequest, 'restaurantId'>;

export interface OwnerMenuItemUpdate extends OwnerMenuItemRequest {
  /** The version the owner saw (409 if somebody changed the item since) */
  version: number;
}

export interface OwnerOperatingHours {
  dayOfWeek: DayOfWeek;
  /** 'HH:mm' */
  opensAt: string;
  closesAt: string;
}

/** PUT /api/v1/restaurants/mine/{id}/operating-hours: the whole week is replaced. */
export interface OperatingHoursUpdate {
  version: number;
  operatingHours: OwnerOperatingHours[];
}

/** The owner's restaurant as written when registering it (POST) and, with a version, when changing it (PUT). */
export interface OwnerRestaurantRequest {
  name: string;
  description: string | null;
  cuisines: string[];
  phone: string;
  email: string;
  address: RestaurantAddress & { latitude?: number | null; longitude?: number | null };
  timeZone: string;
  legalBusinessName: string;
  taxRegistrationNumber: string | null;
  foodLicenseNumber: string;
  documents: { type: string; url: string }[];
  operatingHours: OwnerOperatingHours[];
}

/** PUT /api/v1/restaurants/mine/{id}: a full replacement of the restaurant. */
export interface OwnerRestaurantUpdate extends OwnerRestaurantRequest {
  /** The version the owner saw (409 if somebody changed the restaurant since) */
  version: number;
}
