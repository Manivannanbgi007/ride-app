import type { CategoryStatus, FoodType, MenuItemStatus } from '@/types/catalog';
import type { DeliveryStatus, PartnerStatus, VehicleType } from '@/types/delivery';
import type {
  NotificationChannel,
  NotificationStatus,
  NotificationType,
} from '@/types/notification';
import type {
  CouponAvailability,
  CouponStatus,
  DiscountType,
  RedemptionStatus,
} from '@/types/coupon';
import type { ActorType, OrderStatus, PaymentMethod, PaymentStatus } from '@/types/order';
import type { PaymentRecordStatus, RefundStatus } from '@/types/payment';
import type { ReviewSort, ReviewTag } from '@/types/review';
import type { RevenueGroupBy } from '@/types/report';
import type { AddressType } from '@/types/shopping';
import type { DayOfWeek, DocumentType, RestaurantStatus } from '@/types/restaurant';
import type { UserRole, UserStatus } from '@/types/user';

/** The admin area's addresses. Links and redirects use these constants, never a typed-out path. */
export const ROUTES = {
  admin: '/admin',
  signIn: '/admin/sign-in',
  dashboard: '/admin/dashboard',
  orders: '/admin/orders',
  orderDetails: '/admin/orders/:orderId',
  customers: '/admin/customers',
  customerDetails: '/admin/customers/:userId',
  foods: '/admin/foods',
  foodNew: '/admin/foods/new',
  foodDetails: '/admin/foods/:itemId',
  restaurants: '/admin/restaurants',
  restaurantDetails: '/admin/restaurants/:restaurantId',
  categories: '/admin/categories',
  payments: '/admin/payments',
  paymentDetails: '/admin/payments/:paymentId',
  refunds: '/admin/refunds',
  refundDetails: '/admin/refunds/:refundId',
  deliveries: '/admin/deliveries',
  deliveryDetails: '/admin/deliveries/:deliveryId',
  partnerDetails: '/admin/deliveries/partners/:userId',
  coupons: '/admin/coupons',
  couponNew: '/admin/coupons/new',
  couponDetails: '/admin/coupons/:couponId',
  couponEdit: '/admin/coupons/:couponId/edit',
  reviews: '/admin/reviews',
  calendar: '/admin/calendar',
  chat: '/admin/chat',
  /** The open conversation is optional: one page keeps the list while another conversation opens */
  chatConversation: '/admin/chat/:conversationId?',
  analytics: '/admin/analytics',
  auditLogs: '/admin/audit-logs',
  auditLogDetails: '/admin/audit-logs/:auditId',
  notifications: '/admin/notifications',
  notificationDetails: '/admin/notifications/:notificationId',
  search: '/admin/search',
} as const;

/** Addresses of one record: routeTo.orderDetails(42) -> '/admin/orders/42'. */
export const routeTo = {
  orderDetails: (orderId: number) => `/admin/orders/${orderId}`,
  customerDetails: (userId: number) => `/admin/customers/${userId}`,
  foodDetails: (itemId: number) => `/admin/foods/${itemId}`,
  restaurantDetails: (restaurantId: number) => `/admin/restaurants/${restaurantId}`,
  couponDetails: (couponId: number) => `/admin/coupons/${couponId}`,
  couponEdit: (couponId: number) => `/admin/coupons/${couponId}/edit`,
  paymentDetails: (paymentId: number) => `/admin/payments/${paymentId}`,
  refundDetails: (refundId: number) => `/admin/refunds/${refundId}`,
  deliveryDetails: (deliveryId: number) => `/admin/deliveries/${deliveryId}`,
  partnerDetails: (userId: number) => `/admin/deliveries/partners/${userId}`,
  auditLogDetails: (auditId: number) => `/admin/audit-logs/${auditId}`,
  notificationDetails: (notificationId: number) => `/admin/notifications/${notificationId}`,
  search: (text: string) => `${ROUTES.search}?q=${encodeURIComponent(text)}`,
  chatConversation: (conversationId: number) => `/admin/chat/${conversationId}`,
};

/** The customer area lives under /customer, like the admin area under /admin. */
export const CUSTOMER_BASE = '/customer';

/**
 * The customer area's addresses (everything under /customer). Pages that need a signed-in customer send visitors
 * to "Sign in" first and come back afterwards.
 */
export const CUSTOMER_ROUTES = {
  home: CUSTOMER_BASE,
  restaurants: `${CUSTOMER_BASE}/restaurants`,
  restaurantMenu: `${CUSTOMER_BASE}/restaurants/:restaurantId`,
  search: `${CUSTOMER_BASE}/search`,
  categories: `${CUSTOMER_BASE}/categories`,
  offers: `${CUSTOMER_BASE}/offers`,
  cart: `${CUSTOMER_BASE}/cart`,
  checkout: `${CUSTOMER_BASE}/checkout`,
  dashboard: `${CUSTOMER_BASE}/dashboard`,
  myOrders: `${CUSTOMER_BASE}/orders`,
  orderDetails: `${CUSTOMER_BASE}/orders/:orderId`,
  orderPayment: `${CUSTOMER_BASE}/orders/:orderId/pay`,
  orderLiveTracking: `${CUSTOMER_BASE}/orders/:orderId/track`,
  addresses: `${CUSTOMER_BASE}/account/addresses`,
  profile: `${CUSTOMER_BASE}/account/profile`,
  notifications: `${CUSTOMER_BASE}/notifications`,
  support: `${CUSTOMER_BASE}/support`,
  signIn: `${CUSTOMER_BASE}/sign-in`,
  register: `${CUSTOMER_BASE}/register`,
} as const;

/** The restaurant owner's area lives under /owner, next to /admin and /customer. */
export const OWNER_BASE = '/owner';

/** The owner area's addresses: the owner's own restaurant, orders, menu and reviews. */
export const OWNER_ROUTES = {
  home: OWNER_BASE,
  signIn: `${OWNER_BASE}/sign-in`,
  register: `${OWNER_BASE}/register`,
  orders: `${OWNER_BASE}/orders`,
  orderDetails: `${OWNER_BASE}/orders/:orderId`,
  menu: `${OWNER_BASE}/menu`,
  reviews: `${OWNER_BASE}/reviews`,
  restaurant: `${OWNER_BASE}/restaurant`,
  newRestaurant: `${OWNER_BASE}/restaurant/new`,
} as const;

/** The delivery partner's area lives under /partner, next to /owner, /admin and /customer. */
export const PARTNER_BASE = '/partner';

/** The partner area's addresses: today's work (online, the current delivery), history and profile. */
export const PARTNER_ROUTES = {
  home: PARTNER_BASE,
  signIn: `${PARTNER_BASE}/sign-in`,
  register: `${PARTNER_BASE}/register`,
  deliveries: `${PARTNER_BASE}/deliveries`,
  deliveryDetails: `${PARTNER_BASE}/deliveries/:deliveryId`,
  profile: `${PARTNER_BASE}/profile`,
} as const;

export const partnerRouteTo = {
  deliveryDetails: (deliveryId: number) => `${PARTNER_ROUTES.deliveries}/${deliveryId}`,
  /** Sign in, then back to `returnTo` (a partner-area address) */
  signIn: (returnTo?: string) =>
    returnTo
      ? `${PARTNER_ROUTES.signIn}?returnTo=${encodeURIComponent(returnTo)}`
      : PARTNER_ROUTES.signIn,
};

export const adminRouteTo = {
  /** Sign in, then back to `returnTo` (an admin-area address) */
  signIn: (returnTo?: string) =>
    returnTo ? `${ROUTES.signIn}?returnTo=${encodeURIComponent(returnTo)}` : ROUTES.signIn,
};

export const ownerRouteTo = {
  orderDetails: (orderId: number) => `${OWNER_ROUTES.orders}/${orderId}`,
  /** Sign in, then back to `returnTo` (an owner-area address) */
  signIn: (returnTo?: string) =>
    returnTo
      ? `${OWNER_ROUTES.signIn}?returnTo=${encodeURIComponent(returnTo)}`
      : OWNER_ROUTES.signIn,
};

/**
 * The customer area's addresses before it moved under /customer (they were at the root). The backend (the
 * assistant's links) and old bookmarks still use them, so they are sent on to the new address.
 */
export const LEGACY_CUSTOMER_PREFIXES = [
  '/restaurants',
  '/search',
  '/offers',
  '/cart',
  '/checkout',
  '/orders',
  '/account',
  '/notifications',
  '/support',
  '/sign-in',
  '/register',
] as const;

export const customerRouteTo = {
  restaurantMenu: (restaurantId: number) => `${CUSTOMER_ROUTES.restaurants}/${restaurantId}`,
  search: (text: string) => `${CUSTOMER_ROUTES.search}?q=${encodeURIComponent(text)}`,
  /** The dishes of one food category (the search page, on its Dishes tab, filtered by the category) */
  categoryDishes: (categoryId: number) =>
    `${CUSTOMER_ROUTES.search}?tab=dishes&categoryId=${categoryId}`,
  orderDetails: (orderId: number) => `${CUSTOMER_ROUTES.myOrders}/${orderId}`,
  orderPayment: (orderId: number) => `${CUSTOMER_ROUTES.myOrders}/${orderId}/pay`,
  orderLiveTracking: (orderId: number) => `${CUSTOMER_ROUTES.myOrders}/${orderId}/track`,
  /** Sign in, then back to `returnTo` (a customer-area address) */
  signIn: (returnTo?: string) =>
    returnTo
      ? `${CUSTOMER_ROUTES.signIn}?returnTo=${encodeURIComponent(returnTo)}`
      : CUSTOMER_ROUTES.signIn,
};
/** The platform's currency (order lists carry amounts without it). */
export const DEFAULT_CURRENCY = 'INR';

/** Rows per page of the admin lists (the services allow up to 100). */
export const DEFAULT_PAGE_SIZE = 20;

/** Cards per page of the Foods grid (two rows on a wide screen). */
export const FOOD_PAGE_SIZE = 10;

/** The periods of the dashboard's "Filter Period" list (days; the API allows 1 to 90). */
export const DASHBOARD_PERIOD_OPTIONS = [7, 30, 90] as const;
export type DashboardPeriodDays = (typeof DASHBOARD_PERIOD_OPTIONS)[number];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PAYMENT_PENDING: 'Payment pending',
  PLACED: 'Placed',
  ACCEPTED: 'Accepted',
  PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  REJECTED: 'Rejected',
};

/** Order statuses as the customer reads them (the admin labels are shorter). */
export const CUSTOMER_ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  PAYMENT_PENDING: 'Waiting for your payment',
  PLACED: 'Waiting for the restaurant',
  ACCEPTED: 'Accepted by the restaurant',
  PREPARING: 'Being prepared',
  READY_FOR_PICKUP: 'Ready, waiting for the delivery partner',
  OUT_FOR_DELIVERY: 'On the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  REJECTED: 'Declined by the restaurant',
};

/** A delivery's status as the customer reads it. */
export const CUSTOMER_DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Looking for a delivery partner',
  ASSIGNED: 'Asking a delivery partner',
  ACCEPTED: 'The partner is on the way to the restaurant',
  PICKED_UP: 'On the way to you',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** Who changed an order, as the customer reads it ("Cancelled by you"). */
export const CUSTOMER_ACTOR_LABELS: Record<ActorType, string> = {
  CUSTOMER: 'you',
  RESTAURANT: 'the restaurant',
  DELIVERY_PARTNER: 'the delivery partner',
  ADMIN: 'support',
  SYSTEM: 'the platform',
};

export const ADDRESS_TYPE_LABELS: Record<AddressType, string> = {
  HOME: 'Home',
  WORK: 'Work',
  OTHER: 'Other',
};
export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Pending',
  PAID: 'Paid',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH_ON_DELIVERY: 'Cash on delivery',
  CARD: 'Card',
  UPI: 'UPI',
  WALLET: 'Wallet',
};

export const ACTOR_TYPE_LABELS: Record<ActorType, string> = {
  CUSTOMER: 'Customer',
  RESTAURANT: 'Restaurant',
  ADMIN: 'Admin',
  SYSTEM: 'System',
  DELIVERY_PARTNER: 'Delivery partner',
};

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  CUSTOMER: 'Customer',
  RESTAURANT_ADMIN: 'Restaurant owner',
  DELIVERY_PARTNER: 'Delivery partner',
  PLATFORM_ADMIN: 'Platform admin',
};

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

export const MENU_ITEM_STATUS_LABELS: Record<MenuItemStatus, string> = {
  ACTIVE: 'Listed',
  DISABLED: 'Hidden by owner',
  BLOCKED: 'Blocked by admin',
};

export const FOOD_TYPE_LABELS: Record<FoodType, string> = {
  VEG: 'Veg',
  NON_VEG: 'Non-veg',
  EGG: 'Egg',
};

export const RESTAURANT_STATUS_LABELS: Record<RestaurantStatus, string> = {
  PENDING: 'Waiting for approval',
  APPROVED: 'Approved (not live yet)',
  REJECTED: 'Rejected',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
};

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  FOOD_LICENSE: 'Food license',
  TAX_REGISTRATION: 'Tax registration',
  OWNER_ID_PROOF: "Owner's ID",
  BANK_ACCOUNT_PROOF: 'Bank account proof',
  OTHER: 'Other document',
};

export const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
};

export const CATEGORY_STATUS_LABELS: Record<CategoryStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
};

export const COUPON_STATUS_LABELS: Record<CouponStatus, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Paused',
};

export const DISCOUNT_TYPE_LABELS: Record<DiscountType, string> = {
  PERCENTAGE: 'Percentage',
  FIXED: 'Fixed amount',
};

export const COUPON_AVAILABILITY_LABELS: Record<CouponAvailability, string> = {
  AVAILABLE: 'Usable today',
  SCHEDULED: 'Starts later',
  EXPIRED: 'Expired',
  USED_UP: 'Used up',
  INACTIVE: 'Paused',
};

export const REDEMPTION_STATUS_LABELS: Record<RedemptionStatus, string> = {
  RESERVED: 'Reserved at checkout',
  APPLIED: 'On an order',
  REDEEMED: 'Redeemed (delivered)',
  RELEASED: 'Given back (cancelled)',
  EXPIRED: 'Given back (no order)',
};

export const PAYMENT_RECORD_STATUS_LABELS: Record<PaymentRecordStatus, string> = {
  INITIATED: 'Started',
  PROCESSING: 'Processing',
  SUCCESS: 'Successful',
  FAILED: 'Failed',
  REFUNDED: 'Refunded',
};

export const REFUND_STATUS_LABELS: Record<RefundStatus, string> = {
  REQUESTED: 'Requested',
  PROCESSING: 'Processing',
  SUCCESS: 'Refunded',
  FAILED: 'Failed',
};

export const DELIVERY_STATUS_LABELS: Record<DeliveryStatus, string> = {
  PENDING: 'Waiting for a partner',
  ASSIGNED: 'Offered to a partner',
  ACCEPTED: 'Partner on the way',
  PICKED_UP: 'Picked up',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export const PARTNER_STATUS_LABELS: Record<PartnerStatus, string> = {
  OFFLINE: 'Offline',
  AVAILABLE: 'Available',
  BUSY: 'On a delivery',
  SUSPENDED: 'Suspended',
};

export const NOTIFICATION_STATUS_LABELS: Record<NotificationStatus, string> = {
  PENDING: 'Pending',
  SENT: 'Sent',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
};

export const NOTIFICATION_CHANNEL_LABELS: Record<NotificationChannel, string> = {
  EMAIL: 'Email',
  SMS: 'SMS',
  PUSH: 'Push / in-app',
};

export const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  WELCOME: 'Welcome',
  RESTAURANT_SUBMITTED: 'Restaurant submitted',
  RESTAURANT_APPROVED: 'Restaurant approved',
  RESTAURANT_REJECTED: 'Restaurant rejected',
  RESTAURANT_LIVE: 'Restaurant live',
  RESTAURANT_DEACTIVATED: 'Restaurant deactivated',
  RESTAURANT_SUSPENDED: 'Restaurant suspended',
  ORDER_PLACED: 'Order placed',
  ORDER_ACCEPTED: 'Order accepted',
  ORDER_PREPARING: 'Order preparing',
  ORDER_READY: 'Order ready',
  ORDER_OUT_FOR_DELIVERY: 'Out for delivery',
  ORDER_DELIVERED: 'Order delivered',
  ORDER_CANCELLED: 'Order cancelled',
  PAYMENT_FAILED: 'Payment failed',
  REFUND_COMPLETED: 'Refund completed',
  NEW_ORDER_FOR_RESTAURANT: 'New order (restaurant)',
  ORDER_CANCELLED_FOR_RESTAURANT: 'Order cancelled (restaurant)',
  PARTNER_ON_THE_WAY: 'Partner on the way',
  DELIVERY_OFFERED: 'Delivery offered (partner)',
  DELIVERY_CANCELLED_FOR_PARTNER: 'Delivery cancelled (partner)',
};

export const REVENUE_GROUP_BY_LABELS: Record<RevenueGroupBy, string> = {
  DAY: 'Per day',
  WEEK: 'Per week',
  MONTH: 'Per month',
};

/** Sort choices of the audit log (fields admin-service allows). */
export const AUDIT_LOG_SORT_OPTIONS = [
  { value: 'auditedAt,desc', label: 'Newest first' },
  { value: 'auditedAt,asc', label: 'Oldest first' },
  { value: 'adminUserId,asc', label: 'By admin' },
  { value: 'sourceService,asc', label: 'By service' },
] as const;

export const VEHICLE_TYPE_LABELS: Record<VehicleType, string> = {
  BICYCLE: 'Bicycle',
  SCOOTER: 'Scooter',
  MOTORCYCLE: 'Motorcycle',
  CAR: 'Car',
};

/** Sort choices of the restaurant list (fields restaurant-service allows). */
export const RESTAURANT_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first (approvals)' },
  { value: 'name,asc', label: 'Name A-Z' },
  { value: 'statusChangedAt,desc', label: 'Status changed' },
] as const;

/** Sort choices of the coupon list (fields promotion-service allows). */
export const COUPON_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'validUntil,asc', label: 'Ending soonest' },
  { value: 'timesUsed,desc', label: 'Most used' },
  { value: 'code,asc', label: 'Code A-Z' },
] as const;

/** Sort choices of the customer list (fields user-service allows). */
export const CUSTOMER_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'lastLoginAt,desc', label: 'Last login' },
  { value: 'fullName,asc', label: 'Name A-Z' },
] as const;

/** Sort choices of the food list (fields catalog-service allows). */
export const FOOD_SORT_OPTIONS = [
  { value: 'updatedAt,desc', label: 'Recently changed' },
  { value: 'name,asc', label: 'Name A-Z' },
  { value: 'price,desc', label: 'Price high to low' },
  { value: 'price,asc', label: 'Price low to high' },
] as const;

/** How the services are named on the screen (admin-service lists the ones that did not answer). */
export const SERVICE_LABELS: Record<string, string> = {
  'user-service': 'Users',
  'restaurant-service': 'Restaurants',
  'catalog-service': 'Menus',
  'order-service': 'Orders',
  'payment-service': 'Payments',
  'notification-service': 'Notifications',
  'delivery-service': 'Deliveries',
  'delivery-partner-service': 'Delivery partners',
  'promotion-service': 'Coupons',
};

/** Numbers and dates are shown the Indian way (the platform's business is in India, currency INR). */
export const DISPLAY_LOCALE = 'en-IN';

/** Shown instead of a figure that is unknown (its service did not answer), so it is never mistaken for zero. */
export const NOT_AVAILABLE = '—';

/** What customers can say about an order (review-service's tags). */
export const REVIEW_TAG_LABELS: Record<ReviewTag, string> = {
  DELICIOUS: 'Delicious',
  EXCELLENT: 'Excellent',
  GOOD_SERVICE: 'Good service',
  FAST_DELIVERY: 'Fast delivery',
  GOOD_VALUE: 'Good value',
  GOOD_PACKAGING: 'Good packaging',
};

/** The most tags one review can carry. */
export const REVIEW_MAX_TAGS = 4;
/** The longest review comment review-service accepts. */
export const REVIEW_COMMENT_MAX_LENGTH = 1000;
/** Reviews per page of "Others review", and featured dishes per page of the carousel. */
export const REVIEW_PAGE_SIZE = 10;
export const FEATURED_DISH_PAGE_SIZE = 3;

/** Sort choices of the review list (what review-service allows). */
export const REVIEW_SORT_OPTIONS: readonly { value: ReviewSort; label: string }[] = [
  { value: 'createdAt,desc', label: 'Latest' },
  { value: 'createdAt,asc', label: 'Oldest' },
  { value: 'rating,desc', label: 'Highest rating' },
  { value: 'rating,asc', label: 'Lowest rating' },
];
