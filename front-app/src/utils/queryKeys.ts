import type { AuditLogFilters } from '@/types/audit';
import type { MenuItemFilters } from '@/types/catalog';
import type { CouponFilters, RedemptionStatus } from '@/types/coupon';
import type { DeliveryFilters, PartnerFilters } from '@/types/delivery';
import type { NotificationFilters } from '@/types/notification';
import type { OwnerOrderFilters } from '@/types/owner';
import type { DishRevenuePeriod, OrderFilters, OrderStatus } from '@/types/order';
import type { PaymentFilters, RefundFilters } from '@/types/payment';
import type { ReportParams, RevenueGroupBy } from '@/types/report';
import type { RestaurantFilters } from '@/types/restaurant';
import type {
  FoodSearchParams,
  RestaurantSearchParams,
  StoreRestaurantFilters,
} from '@/types/storefront';
import type { LikedDishesPeriod, ReviewFilters, ReviewScope } from '@/types/review';
import type { UserFilters } from '@/types/user';

/**
 * The keys under which React Query keeps API data. One list, so the same data always has the same key.
 * Keys are nested: invalidating QUERY_KEYS.orders.all reloads every order list, order and order history at once
 * (what a change such as "cancel order" needs).
 */
export const QUERY_KEYS = {
  currentUser: (userId: number) => ['users', 'me', userId] as const,
  unreadNotificationCount: (userId: number) => ['notifications', 'unread-count', userId] as const,
  /** Everything of the acting user's inbox: the list and the number on the bell */
  inboxAll: ['notifications'] as const,
  inbox: (userId: number, unreadOnly: boolean, page: number) =>
    ['notifications', 'inbox', userId, unreadOnly, page] as const,
  dashboard: (days: number) => ['admin', 'dashboard', days] as const,
  dashboardAll: ['admin', 'dashboard'] as const,
  calendar: (from: string, to: string) => ['admin', 'calendar', from, to] as const,
  orderActivity: (fromDate: string, toDate: string) =>
    ['admin', 'dashboard', 'order-activity', fromDate, toDate] as const,
  /** The AI assistant, per user (a different user never sees another one's conversation) */
  assistant: {
    all: ['assistant'] as const,
    status: (userId: number) => ['assistant', userId, 'status'] as const,
    suggestions: (userId: number, role: string) =>
      ['assistant', userId, 'suggestions', role] as const,
    conversation: (userId: number, conversationId: string) =>
      ['assistant', userId, 'conversation', conversationId] as const,
  },
  /** Everything of the signed-in delivery partner: dropped when they sign in or out */
  partnerAreaAll: ['partner-area'] as const,
  partnerArea: {
    all: (partnerId: number) => ['partner-area', partnerId] as const,
    me: (partnerId: number) => ['partner-area', partnerId, 'me'] as const,
    current: (partnerId: number) => ['partner-area', partnerId, 'current'] as const,
    history: (partnerId: number, status: string, page: number, size: number) =>
      ['partner-area', partnerId, 'history', status, page, size] as const,
    delivery: (partnerId: number, deliveryId: number) =>
      ['partner-area', partnerId, 'delivery', deliveryId] as const,
  },
  /** Everything of the signed-in restaurant owner: dropped when they sign in or out */
  ownerAll: ['owner'] as const,
  owner: {
    restaurants: (ownerId: number) => ['owner', ownerId, 'restaurants'] as const,
    restaurant: (ownerId: number, restaurantId: number) =>
      ['owner', ownerId, 'restaurants', restaurantId] as const,
    /** Every order list, order and number of all the owner's restaurants */
    allOrders: (ownerId: number) => ['owner', ownerId, 'orders'] as const,
    /** Every order list, order and number built from the orders of one restaurant */
    ordersAll: (ownerId: number, restaurantId: number) =>
      ['owner', ownerId, 'orders', restaurantId] as const,
    orders: (
      ownerId: number,
      restaurantId: number,
      filters: OwnerOrderFilters,
      page: number,
      size: number,
    ) => ['owner', ownerId, 'orders', restaurantId, 'list', filters, page, size] as const,
    order: (ownerId: number, restaurantId: number, orderId: number) =>
      ['owner', ownerId, 'orders', restaurantId, 'order', orderId] as const,
    /** Under ordersAll: an order action (accept, ready, ...) reloads the counts with the lists */
    orderCount: (ownerId: number, restaurantId: number, filters: OwnerOrderFilters) =>
      ['owner', ownerId, 'orders', restaurantId, 'count', filters] as const,
    sales: (ownerId: number, restaurantId: number, fromDate: string, toDate: string) =>
      ['owner', ownerId, 'orders', restaurantId, 'sales', fromDate, toDate] as const,
    topItems: (ownerId: number, restaurantId: number, fromDate: string, toDate: string) =>
      ['owner', ownerId, 'orders', restaurantId, 'top-items', fromDate, toDate] as const,
    menu: (ownerId: number, restaurantId: number) =>
      ['owner', ownerId, 'menu', restaurantId] as const,
  },
  /** Everything of the signed-in customer (cart, addresses, orders): dropped when they sign in or out */
  customerAll: ['customer'] as const,
  customer: {
    cart: (customerId: number) => ['customer', customerId, 'cart'] as const,
    addresses: (customerId: number) => ['customer', customerId, 'addresses'] as const,
    order: (customerId: number, orderId: number) =>
      ['customer', customerId, 'orders', orderId] as const,
    /** With the page size: "My orders" and the dashboard ask for pages of different sizes */
    orders: (customerId: number, status: OrderStatus | undefined, page: number, size: number) =>
      ['customer', customerId, 'orders', 'list', status, page, size] as const,
    allOrders: (customerId: number) => ['customer', customerId, 'orders'] as const,
    // The dashboard's figures sit under the orders: whatever refreshes the orders refreshes them too
    orderSummary: (customerId: number) => ['customer', customerId, 'orders', 'summary'] as const,
    orderActivity: (customerId: number, fromDate: string, toDate: string) =>
      ['customer', customerId, 'orders', 'activity', fromDate, toDate] as const,
    favouriteDishes: (customerId: number, limit: number) =>
      ['customer', customerId, 'orders', 'favourite-dishes', limit] as const,
    tracking: (customerId: number, orderId: number) =>
      ['customer', customerId, 'tracking', orderId] as const,
    allTracking: (customerId: number) => ['customer', customerId, 'tracking'] as const,
    liveTracking: (customerId: number, orderId: number) =>
      ['customer', customerId, 'tracking', orderId, 'live'] as const,
  },
  /** Public data of the customer area (the same for everybody) */
  storefront: {
    restaurants: (filters: StoreRestaurantFilters, page: number, size: number) =>
      ['storefront', 'restaurants', filters, page, size] as const,
    restaurant: (restaurantId: number) => ['storefront', 'restaurant', restaurantId] as const,
    menu: (restaurantId: number) => ['storefront', 'menu', restaurantId] as const,
    categories: ['storefront', 'categories'] as const,
    /** Whether online payments use Razorpay Checkout (the same for every customer) */
    razorpayConfig: ['storefront', 'razorpay-config'] as const,
    offers: (restaurantId: number | null) => ['storefront', 'offers', restaurantId] as const,
    searchRestaurants: (params: RestaurantSearchParams, page: number) =>
      ['storefront', 'search', 'restaurants', params, page] as const,
    /** With the page size: the search page and the dashboard's suggestions ask for pages of different sizes */
    searchFoods: (params: FoodSearchParams, page: number, size: number) =>
      ['storefront', 'search', 'foods', params, page, size] as const,
    suggest: (text: string) => ['storefront', 'suggest', text] as const,
  },
  /** Every report and analytics figure of the admin area */
  reportsAll: ['admin', 'reports'] as const,
  monthlyRevenue: (year: number) => ['admin', 'reports', 'revenue', 'monthly', year] as const,
  reports: {
    orders: (params: ReportParams) => ['admin', 'reports', 'orders', params] as const,
    revenue: (params: ReportParams, groupBy: RevenueGroupBy) =>
      ['admin', 'reports', 'revenue', params, groupBy] as const,
    restaurants: (params: ReportParams) => ['admin', 'reports', 'restaurants', params] as const,
    customers: (params: ReportParams) => ['admin', 'reports', 'customers', params] as const,
    deliveries: (params: ReportParams) => ['admin', 'reports', 'deliveries', params] as const,
  },
  auditLogs: {
    all: ['admin', 'audit-logs'] as const,
    list: (filters: AuditLogFilters, page: number, size: number) =>
      ['admin', 'audit-logs', 'list', filters, page, size] as const,
    actions: ['admin', 'audit-logs', 'actions'] as const,
    details: (auditId: number) => ['admin', 'audit-logs', 'details', auditId] as const,
  },
  /** Support chat. Staff: the shared inbox; mine: the acting participant's own conversation (per user). */
  chat: {
    staffAll: ['chat', 'staff'] as const,
    conversationsAll: ['chat', 'staff', 'conversations'] as const,
    conversations: (search: string) => ['chat', 'staff', 'conversations', search] as const,
    conversation: (id: number) => ['chat', 'staff', 'conversation', id] as const,
    messages: (id: number, search: string) => ['chat', 'staff', 'messages', id, search] as const,
    unreadCount: ['chat', 'staff', 'unread-count'] as const,
    mineAll: (userId: number) => ['chat', 'mine', userId] as const,
    myConversation: (userId: number) => ['chat', 'mine', userId, 'conversation'] as const,
    myMessages: (userId: number) => ['chat', 'mine', userId, 'messages'] as const,
  },
  adminNotifications: {
    all: ['admin', 'notifications'] as const,
    list: (filters: NotificationFilters, page: number, size: number) =>
      ['admin', 'notifications', 'list', filters, page, size] as const,
    summary: (fromDate?: string, toDate?: string) =>
      ['admin', 'notifications', 'summary', fromDate, toDate] as const,
    details: (notificationId: number) =>
      ['admin', 'notifications', 'details', notificationId] as const,
    auditLogs: (notificationId: number) =>
      ['admin', 'notifications', 'audit-logs', notificationId] as const,
  },

  orders: {
    all: ['admin', 'orders'] as const,
    list: (filters: OrderFilters, page: number, size: number) =>
      ['admin', 'orders', 'list', filters, page, size] as const,
    details: (orderId: number) => ['admin', 'orders', 'details', orderId] as const,
    auditLogs: (orderId: number) => ['admin', 'orders', 'audit-logs', orderId] as const,
    customerSummary: (customerId: number) =>
      ['admin', 'orders', 'customer-summary', customerId] as const,
    dishRevenue: (menuItemId: number, period: DishRevenuePeriod) =>
      ['admin', 'orders', 'dish-revenue', menuItemId, period] as const,
    customerTopFoods: (customerId: number, period: DishRevenuePeriod, limit: number) =>
      ['admin', 'orders', 'customer-top-foods', customerId, period, limit] as const,
  },
  users: {
    all: ['admin', 'users'] as const,
    list: (filters: UserFilters, page: number, size: number) =>
      ['admin', 'users', 'list', filters, page, size] as const,
    details: (userId: number) => ['admin', 'users', 'details', userId] as const,
    auditLogs: (userId: number) => ['admin', 'users', 'audit-logs', userId] as const,
    addresses: (userId: number) => ['admin', 'users', 'addresses', userId] as const,
  },
  menuItems: {
    all: ['admin', 'menu-items'] as const,
    list: (filters: MenuItemFilters, page: number, size: number) =>
      ['admin', 'menu-items', 'list', filters, page, size] as const,
    details: (itemId: number) => ['admin', 'menu-items', 'details', itemId] as const,
    categoryStats: (limit: number) => ['admin', 'menu-items', 'category-stats', limit] as const,
    auditLogs: (itemId: number) => ['admin', 'menu-items', 'audit-logs', itemId] as const,
  },
  categories: ['admin', 'categories'] as const,
  categoryAuditLogs: (categoryId: number) =>
    ['admin', 'categories', 'audit-logs', categoryId] as const,
  restaurants: {
    all: ['admin', 'restaurants'] as const,
    list: (filters: RestaurantFilters, page: number, size: number) =>
      ['admin', 'restaurants', 'list', filters, page, size] as const,
    details: (restaurantId: number) => ['admin', 'restaurants', 'details', restaurantId] as const,
    auditLogs: (restaurantId: number) =>
      ['admin', 'restaurants', 'audit-logs', restaurantId] as const,
  },
  coupons: {
    all: ['admin', 'coupons'] as const,
    list: (filters: CouponFilters, page: number, size: number) =>
      ['admin', 'coupons', 'list', filters, page, size] as const,
    details: (couponId: number) => ['admin', 'coupons', 'details', couponId] as const,
    redemptions: (couponId: number, status: RedemptionStatus | undefined, page: number) =>
      ['admin', 'coupons', 'redemptions', couponId, status, page] as const,
    auditLogs: (couponId: number) => ['admin', 'coupons', 'audit-logs', couponId] as const,
  },
  reviews: {
    all: ['admin', 'reviews'] as const,
    list: (filters: ReviewFilters, size: number) =>
      ['admin', 'reviews', 'list', filters, size] as const,
    summary: (scope: ReviewScope) => ['admin', 'reviews', 'summary', scope] as const,
    featured: (scope: ReviewScope, page: number, size: number) =>
      ['admin', 'reviews', 'featured', scope, page, size] as const,
    customerLikedDishes: (customerId: number, period: LikedDishesPeriod, limit: number) =>
      ['admin', 'reviews', 'customer-liked-dishes', customerId, period, limit] as const,
  },
  /** Public ratings of restaurants and dishes (shown on cards, lists and detail pages) */
  ratings: {
    all: ['ratings'] as const,
    restaurants: (ids: readonly number[]) => ['ratings', 'restaurants', ids] as const,
    dishes: (ids: readonly number[]) => ['ratings', 'dishes', ids] as const,
    restaurant: (restaurantId: number) => ['ratings', 'restaurant', restaurantId] as const,
    dish: (menuItemId: number) => ['ratings', 'dish', menuItemId] as const,
    restaurantReviews: (restaurantId: number, sort: string, page: number) =>
      ['ratings', 'restaurant-reviews', restaurantId, sort, page] as const,
    dishReviews: (menuItemId: number, page: number) =>
      ['ratings', 'dish-reviews', menuItemId, page] as const,
  },
  /** A customer's review of one order (404 until they write it) */
  orderReview: (customerId: number, orderId: number) =>
    ['customer', customerId, 'review', orderId] as const,
  payments: {
    all: ['admin', 'payments'] as const,
    list: (filters: PaymentFilters, page: number, size: number) =>
      ['admin', 'payments', 'list', filters, page, size] as const,
    summary: (fromDate?: string, toDate?: string) =>
      ['admin', 'payments', 'summary', fromDate, toDate] as const,
    details: (paymentId: number) => ['admin', 'payments', 'details', paymentId] as const,
    auditLogs: (paymentId: number) => ['admin', 'payments', 'audit-logs', paymentId] as const,
  },
  deliveries: {
    all: ['admin', 'deliveries'] as const,
    list: (filters: DeliveryFilters, page: number, size: number) =>
      ['admin', 'deliveries', 'list', filters, page, size] as const,
    delayed: (page: number) => ['admin', 'deliveries', 'delayed', page] as const,
    summary: (fromDate?: string, toDate?: string) =>
      ['admin', 'deliveries', 'summary', fromDate, toDate] as const,
    details: (deliveryId: number) => ['admin', 'deliveries', 'details', deliveryId] as const,
    auditLogs: (deliveryId: number) => ['admin', 'deliveries', 'audit-logs', deliveryId] as const,
  },
  partners: {
    all: ['admin', 'partners'] as const,
    list: (filters: PartnerFilters, page: number, size: number) =>
      ['admin', 'partners', 'list', filters, page, size] as const,
    availability: ['admin', 'partners', 'availability'] as const,
    details: (userId: number) => ['admin', 'partners', 'details', userId] as const,
    deliveries: (userId: number, page: number) =>
      ['admin', 'partners', 'deliveries', userId, page] as const,
    auditLogs: (userId: number) => ['admin', 'partners', 'audit-logs', userId] as const,
  },
  refunds: {
    all: ['admin', 'refunds'] as const,
    list: (filters: RefundFilters, page: number, size: number) =>
      ['admin', 'refunds', 'list', filters, page, size] as const,
    details: (refundId: number) => ['admin', 'refunds', 'details', refundId] as const,
  },
};
