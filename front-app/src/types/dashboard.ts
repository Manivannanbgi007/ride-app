/**
 * GET /api/v1/admin/dashboard?days= (admin-service DashboardResponse). Figures of a service that did not answer are
 * null and the service is named in `unavailable`. Amounts are numbers in `currency`; dates are 'YYYY-MM-DD'.
 */
export interface DashboardResponse {
  today: string;
  chartsFrom: string;
  currency: string;
  generatedAt: string;
  summary: DashboardSummary;
  charts: DashboardCharts;
  unavailable: string[];
}

export interface DashboardSummary {
  totalCustomers: number | null;
  totalRestaurants: number | null;
  activeRestaurants: number | null;
  inactiveRestaurants: number | null;
  pendingRestaurantApprovals: number | null;
  totalMenuItems: number | null;
  activeMenuItems: number | null;
  todaysOrders: number | null;
  todaysCompletedOrders: number | null;
  todaysCancelledOrders: number | null;
  pendingOrders: number | null;
  todaysRevenue: number | null;
  totalRevenue: number | null;
  todaysSuccessfulPayments: number | null;
  todaysFailedPayments: number | null;
  activeDeliveryPartners: number | null;
  pendingDeliveryAssignments: number | null;
}

/**
 * GET /api/v1/admin/dashboard/order-activity?fromDate=&toDate= (admin-service OrderActivityResponse): the order
 * charts of one chosen day, week or month. A last day still to come is cut at today (toDate says where it ends).
 */
export interface OrderActivityResponse {
  fromDate: string;
  toDate: string;
  currency: string | null;
  /** Every day of the period, also days without orders */
  ordersPerDay: DayPoint[];
  paymentOutcome: PaymentOutcome | null;
  unavailable: string[];
}

export interface DashboardCharts {
  /** Every day of the period, also days without orders */
  ordersPerDay: DayPoint[];
  /** The period's orders by their status now */
  ordersByStatus: Record<string, number>;
  paymentOutcome: PaymentOutcome | null;
  restaurantPerformance: RestaurantPoint[];
  deliveryPerformance: DeliveryPerformance | null;
}

export interface DayPoint {
  date: string;
  orders: number;
  completed: number;
  cancelled: number;
  revenue: number;
}

/** Online payments of the period; successful includes the ones refunded since. */
export interface PaymentOutcome {
  successful: number;
  failed: number;
  /** Payments refunded in full */
  refunded: number;
  /** The money returned on these payments, full and partial refunds, any order (absent from older answers) */
  refundedAmount?: number | null;
}

export interface RestaurantPoint {
  restaurantId: number;
  restaurantName: string;
  orders: number;
  completed: number;
  revenue: number;
  /** Percent, e.g. 25.0 */
  cancellationRate: number;
}

export interface DeliveryPerformance {
  delivered: number;
  cancelled: number;
  averageDeliveryMinutes: number | null;
  delayedNow: number;
  topPartners: PartnerPoint[];
}

export interface PartnerPoint {
  userId: number;
  name: string;
  delivered: number;
  averageDeliveryMinutes: number | null;
}

/** One month of the "this year against last year" revenue chart. */
export interface MonthlyRevenuePoint {
  month: string;
  thisYear: number | null;
  lastYear: number | null;
}

export interface MonthlyRevenueComparison {
  currency: string;
  thisYear: number;
  lastYear: number;
  months: MonthlyRevenuePoint[];
  unavailable: string[];
}
