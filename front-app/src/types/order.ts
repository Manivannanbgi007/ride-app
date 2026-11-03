/** Order statuses of order-service, in the order an order moves through them. */
export const ORDER_STATUSES = [
  'PAYMENT_PENDING',
  'PLACED',
  'ACCEPTED',
  'PREPARING',
  'READY_FOR_PICKUP',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'CANCELLED',
  'REJECTED',
] as const;
export type OrderStatus = (typeof ORDER_STATUSES)[number];

/**
 * Statuses an admin may still cancel (order-service OrderStatus: everything before OUT_FOR_DELIVERY that is not
 * finished). The server checks it again; this only decides whether the button is shown.
 */
export const ADMIN_CANCELLABLE_STATUSES: readonly OrderStatus[] = [
  'PAYMENT_PENDING',
  'PLACED',
  'ACCEPTED',
  'PREPARING',
  'READY_FOR_PICKUP',
];

export const PAYMENT_STATUSES = ['PENDING', 'PAID', 'FAILED', 'REFUNDED'] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

export const PAYMENT_METHODS = ['CASH_ON_DELIVERY', 'CARD', 'UPI', 'WALLET'] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

/** Who changed an order (timeline, cancellation). */
export type ActorType = 'CUSTOMER' | 'RESTAURANT' | 'ADMIN' | 'SYSTEM' | 'DELIVERY_PARTNER';

/** One row of an order list (OrderSummaryResponse). */
export interface OrderSummary {
  id: number;
  customerId: number;
  /** The customer's name when they ordered (null for older orders; absent from older answers) */
  customerName?: string | null;
  restaurantId: number;
  restaurantName: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  totalAmount: number;
  itemCount: number;
  createdAt: string;
  /** The ordered dishes, as ordered (absent from older answers) */
  items?: OrderSummaryItem[];
}

/** One dish of an order in a list: what and how many (its picture is the restaurant's menu's). */
export interface OrderSummaryItem {
  menuItemId: number;
  name: string;
  /** VEG, NON_VEG or EGG as it was when ordered */
  foodType: string | null;
  quantity: number;
}

/** A whole order with its timeline (OrderResponse). */
export interface OrderDetails {
  id: number;
  customerId: number;
  customerName: string | null;
  customerPhone: string | null;
  restaurantId: number;
  restaurantName: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  items: OrderLine[];
  price: PriceBreakdown;
  couponCode: string | null;
  deliveryAddress: DeliveryAddress | null;
  notes: string | null;
  cancellationReason: string | null;
  cancelledBy: ActorType | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  timeline: TimelineEntry[];
}

export interface OrderLine {
  menuItemId: number;
  name: string;
  foodType: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface PriceBreakdown {
  itemsTotal: number;
  discountAmount: number;
  deliveryFee: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
}

export interface DeliveryAddress {
  line1: string;
  line2: string | null;
  city: string;
  state: string | null;
  postalCode: string | null;
  country: string | null;
}

export interface TimelineEntry {
  status: OrderStatus;
  actorType: ActorType;
  actorUserId: number | null;
  note: string | null;
  at: string;
}

/** A customer's orders in numbers (CustomerOrderSummaryResponse), for the customer screen. */
export interface CustomerOrderSummary {
  customerId: number;
  totalOrders: number;
  activeOrders: number;
  deliveredOrders: number;
  cancelledOrders: number;
  rejectedOrders: number;
  totalSpent: number;
  lastOrderAt: string | null;
  currency: string;
}

/**
 * GET /api/v1/orders/activity: what the signed-in customer ordered in a period (their dashboard's charts). Days
 * are business days, both ends included; spent = what the delivered orders cost.
 */
export interface CustomerOrderActivity {
  fromDate: string;
  toDate: string;
  totalOrders: number;
  /** The period's orders by the status they have now (statuses without orders are left out) */
  ordersByStatus: Partial<Record<OrderStatus, number>>;
  spent: number;
  currency: string;
  /** Every day of the period: placed, delivered, cancelled or rejected, and spent (revenue) */
  perDay: {
    date: string;
    orders: number;
    completed: number;
    cancelled: number;
    revenue: number;
  }[];
}

/**
 * GET /api/v1/orders/favourite-dishes: a dish the customer ordered before, with today's price and stock from the
 * catalog (what adding it to the cart would cost now).
 */
export interface FavouriteDish {
  menuItemId: number;
  name: string;
  restaurantId: number;
  restaurantName: string;
  /** Null when the dish is no longer on any menu */
  price: number | null;
  foodType: 'VEG' | 'NON_VEG' | 'EGG' | null;
  imageUrl: string | null;
  categoryName: string | null;
  /** On the menu and in stock now: it can be added to the cart */
  available: boolean;
  /** In how many of the customer's orders it was */
  timesOrdered: number;
  quantity: number;
  lastOrderedAt: string;
}

/** The order list's filters (all optional; dates 'YYYY-MM-DD', inclusive). */
export interface OrderFilters {
  orderId?: number;
  customerId?: number;
  restaurantId?: number;
  status?: OrderStatus;
  paymentStatus?: PaymentStatus;
  fromDate?: string;
  toDate?: string;
}

/** The windows of a dish's revenue chart: the last 12 months, 12 weeks or 30 days. */
export const DISH_REVENUE_PERIODS = ['MONTHLY', 'WEEKLY', 'DAILY'] as const;
export type DishRevenuePeriod = (typeof DISH_REVENUE_PERIODS)[number];

export interface DishRevenuePoint {
  /** The axis label: 'Jan', '5 Oct', ... */
  label: string;
  /** First day of the window, 'YYYY-MM-DD' */
  start: string;
  /** INR, delivered orders only */
  revenue: number;
  quantity: number;
  orders: number;
}

/** A dish's revenue from delivered orders (DishRevenueResponse); windows without sales are included with 0. */
export interface DishRevenue {
  menuItemId: number;
  period: DishRevenuePeriod;
  totalRevenue: number;
  totalQuantity: number;
  points: DishRevenuePoint[];
}

/** One dish a customer orders often (delivered orders only). */
export interface CustomerTopFood {
  menuItemId: number;
  name: string;
  /** In how many orders the dish was */
  orders: number;
  /** Pieces ordered */
  quantity: number;
  /** INR spent on the dish */
  totalSpent: number;
  unitPrice: number;
  /** ISO-8601 instant */
  lastOrderedAt: string;
  imageUrl: string | null;
  categoryName: string | null;
}

/** The dishes a customer orders most in the period (MONTHLY = last 30 days, WEEKLY = last 7, DAILY = today). */
export interface CustomerTopFoods {
  customerId: number;
  period: DishRevenuePeriod;
  /** First day of the period, 'YYYY-MM-DD' */
  from: string;
  items: CustomerTopFood[];
}
