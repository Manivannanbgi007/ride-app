/**
 * admin-service reports (GET /api/v1/admin/reports/...). Each covers calendar days in the business time zone
 * (both included, at most 366). The figures of a service that did not answer are null (lists: empty) and the
 * service is named in `unavailable`: never show those as zero. Amounts are numbers in `currency`.
 */
import type { DayPoint, RestaurantPoint } from '@/types/dashboard';

/** The period and how many rows each list has (1 to 100). */
export interface ReportParams {
  fromDate: string;
  toDate: string;
  limit?: number;
}

/** Orders of the period: completed = delivered, cancelled = cancelled or rejected, pending = not finished yet. */
export interface OrderReport {
  fromDate: string;
  toDate: string;
  currency: string | null;
  totalOrders: number | null;
  completed: number | null;
  cancelled: number | null;
  pending: number | null;
  averageOrderValue: number | null;
  /** Order status -> orders; only statuses that have orders */
  byStatus: Record<string, number>;
  /** Every day of the period */
  perDay: DayPoint[];
  /** The restaurants with the most revenue */
  byRestaurant: RestaurantPoint[];
  /** By delivery city */
  byCity: CityRow[];
  unavailable: string[];
}

export interface CityRow {
  city: string;
  orders: number;
  completed: number;
  revenue: number;
}

export type RevenueGroupBy = 'DAY' | 'WEEK' | 'MONTH';

/** Revenue: what customers paid for delivered orders, by the day the order was placed. */
export interface RevenueReport {
  fromDate: string;
  toDate: string;
  currency: string | null;
  groupBy: RevenueGroupBy;
  revenue: number | null;
  discounts: number | null;
  averageOrderValue: number | null;
  /** Per day, week or month (groupBy); the first and last can be partial */
  periods: RevenuePeriod[];
  byRestaurant: RestaurantPoint[];
  /** Payment method -> delivered orders and revenue (cash on delivery included) */
  byPaymentMethod: Record<string, MethodRevenue>;
  /** Money taken and given back online in the period; null when payment-service did not answer */
  onlinePayments: OnlinePayments | null;
  unavailable: string[];
}

export interface RevenuePeriod {
  /** '2026-10-07', '2026-W41' or '2026-10' */
  label: string;
  from: string;
  to: string;
  orders: number;
  completed: number;
  revenue: number;
}

export interface MethodRevenue {
  orders: number;
  completed: number;
  revenue: number;
}

export interface OnlinePayments {
  collected: number;
  refunded: number;
}

/** Restaurants right now (counts) and in the period (orders and revenue). */
export interface RestaurantReport {
  fromDate: string;
  toDate: string;
  currency: string | null;
  total: number | null;
  active: number | null;
  inactive: number | null;
  suspended: number | null;
  pendingApproval: number | null;
  /** Submitted in the period */
  newRestaurants: number | null;
  /** Restaurant status -> restaurants now */
  byStatus: Record<string, number>;
  /** Received at least one order in the period */
  restaurantsWithOrders: number | null;
  topRestaurants: RestaurantPoint[];
  /** Restaurants with orders, least revenue first (restaurants without any order are not listed) */
  lowPerforming: RestaurantPoint[];
  mostCancellations: RestaurantPoint[];
  unavailable: string[];
}

/** The order-frequency buckets, always all four, in this order. */
export const ORDER_FREQUENCY_BUCKETS = ['1', '2-3', '4-5', '6+'] as const;

export interface CustomerReport {
  fromDate: string;
  toDate: string;
  currency: string | null;
  /** Customer accounts now */
  totalCustomers: number | null;
  activeAccounts: number | null;
  /** Deactivated by an admin */
  deactivatedAccounts: number | null;
  /** Signed up in the period */
  newCustomers: number | null;
  /** Placed at least one order in the period ("active customers") */
  orderingCustomers: number | null;
  /** Customer accounts without an order in the period ("inactive customers") */
  notOrdering: number | null;
  ordersPerCustomer: number | null;
  /** '1' | '2-3' | '4-5' | '6+' -> customers who placed that many orders in the period */
  orderFrequency: Record<string, number>;
  /** The customers who spent most */
  topCustomers: CustomerRow[];
  unavailable: string[];
}

export interface CustomerRow {
  customerId: number;
  customerName: string;
  orders: number;
  completed: number;
  spent: number;
}

/** Deliveries started in the period and partner performance (admin-service DeliveryReportResponse). */
export interface DeliveryPerformanceReport {
  fromDate: string;
  toDate: string;
  total: number | null;
  /** Delivered */
  completed: number | null;
  /** Cancelled before reaching the customer */
  failed: number | null;
  /** From "food ready" to "delivered"; null also when nothing was delivered */
  averageDeliveryMinutes: number | null;
  /** Running late right now (whatever the period) */
  delayedNow: number | null;
  /** Delivery status -> deliveries, every status (zero-filled) */
  byStatus: Record<string, number>;
  /** Available or on a delivery right now */
  partnersOnline: number | null;
  /** Ready but not accepted by a partner yet, right now */
  waitingForPartner: number | null;
  topPartners: PartnerRow[];
  unavailable: string[];
}

export interface PartnerRow {
  userId: number;
  name: string;
  delivered: number;
  averageDeliveryMinutes: number | null;
  /** Offers the partner let expire */
  offersMissed: number;
}
