import type { ChatConversation, ChatMessage, ChatMessagePage } from '@/types/chat';
import type { PageResponse } from '@/types/api';
import type { CalendarEvent, CalendarResponse } from '@/types/calendar';
import type { Category, CategoryStat, MenuItem } from '@/types/catalog';
import type { Coupon, CouponDetails } from '@/types/coupon';
import type { Payment, Refund } from '@/types/payment';
import type { Address, Cart } from '@/types/shopping';
import type {
  DashboardResponse,
  MonthlyRevenueComparison,
  OrderActivityResponse,
} from '@/types/dashboard';
import type { Delivery, DeliverySummary, Partner } from '@/types/delivery';
import type { CustomerTopFood, DishRevenue, OrderDetails, OrderSummary } from '@/types/order';
import type {
  AdminRestaurantDetails,
  RestaurantDetails,
  RestaurantOwner,
  RestaurantSummary,
} from '@/types/restaurant';
import type { FeaturedDish, LikedDish, Review, ReviewSummary } from '@/types/review';
import type { AdminUserDetails, UserProfile } from '@/types/user';

/** A one-page answer of a list API. */
export function pageOf<T>(
  content: T[],
  totalElements = content.length,
  page = 0,
  size = 20,
): PageResponse<T> {
  const totalPages = Math.max(1, Math.ceil(totalElements / size));
  return {
    content,
    page,
    size,
    totalElements,
    totalPages,
    first: page === 0,
    last: page >= totalPages - 1,
  };
}

export function orderSummary(overrides: Partial<OrderSummary> = {}): OrderSummary {
  return {
    id: 42,
    customerId: 7,
    restaurantId: 3,
    restaurantName: 'Biryani House',
    status: 'PLACED',
    paymentMethod: 'UPI',
    paymentStatus: 'PAID',
    totalAmount: 562.9,
    itemCount: 2,
    createdAt: '2026-10-08T10:15:00Z',
    ...overrides,
  };
}

export function orderDetails(overrides: Partial<OrderDetails> = {}): OrderDetails {
  return {
    id: 42,
    customerId: 7,
    customerName: 'Priya Sharma',
    customerPhone: '+91 98765 43210',
    restaurantId: 3,
    restaurantName: 'Biryani House',
    status: 'PLACED',
    paymentMethod: 'UPI',
    paymentStatus: 'PAID',
    items: [
      {
        menuItemId: 11,
        name: 'Chicken Biryani',
        foodType: 'NON_VEG',
        unitPrice: 299,
        quantity: 2,
        lineTotal: 598,
      },
    ],
    price: {
      itemsTotal: 598,
      discountAmount: 100,
      deliveryFee: 40,
      taxAmount: 24.9,
      totalAmount: 562.9,
      currency: 'INR',
    },
    couponCode: 'SAVE100',
    deliveryAddress: {
      line1: '12 MG Road',
      line2: null,
      city: 'Bengaluru',
      state: 'KA',
      postalCode: '560001',
      country: 'IN',
    },
    notes: null,
    cancellationReason: null,
    cancelledBy: null,
    createdAt: '2026-10-08T10:15:00Z',
    updatedAt: '2026-10-08T10:16:00Z',
    version: 1,
    timeline: [
      {
        status: 'PAYMENT_PENDING',
        actorType: 'CUSTOMER',
        actorUserId: 7,
        note: null,
        at: '2026-10-08T10:15:00Z',
      },
      {
        status: 'PLACED',
        actorType: 'SYSTEM',
        actorUserId: null,
        note: 'Payment received',
        at: '2026-10-08T10:16:00Z',
      },
    ],
    ...overrides,
  };
}

export function adminUser(overrides: Partial<AdminUserDetails> = {}): AdminUserDetails {
  return {
    id: 7,
    fullName: 'Priya Sharma',
    email: 'priya@example.com',
    phone: '+91 98765 43210',
    role: 'CUSTOMER',
    status: 'ACTIVE',
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
    lastLoginAt: '2026-10-08T08:00:00Z',
    addressCount: 2,
    ...overrides,
  };
}

export function restaurantDetails(overrides: Partial<RestaurantDetails> = {}): RestaurantDetails {
  return {
    id: 3,
    ownerUserId: 20,
    name: 'Biryani House',
    description: 'Hyderabadi biryani since 1998',
    cuisines: ['Biryani', 'North Indian'],
    phone: '+91 80 4000 1234',
    email: 'hello@biryanihouse.example.com',
    address: {
      line1: '12 MG Road',
      line2: null,
      city: 'Bengaluru',
      state: 'KA',
      postalCode: '560001',
      country: 'IN',
    },
    timeZone: 'Asia/Kolkata',
    legalBusinessName: 'Biryani House Pvt Ltd',
    taxRegistrationNumber: '29ABCDE1234F1Z5',
    foodLicenseNumber: '11223344556677',
    status: 'ACTIVE',
    statusReason: 'Food license verified',
    statusChangedAt: '2026-09-02T09:00:00Z',
    approvedAt: '2026-09-02T08:00:00Z',
    operatingHours: [{ dayOfWeek: 'MONDAY', opensAt: '11:00:00', closesAt: '23:00:00' }],
    documents: [
      {
        type: 'FOOD_LICENSE',
        url: 'https://docs.example.com/license.pdf',
        submittedAt: '2026-09-01T09:00:00Z',
      },
    ],
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-09-02T09:00:00Z',
    version: 3,
    ...overrides,
  };
}

export function adminRestaurant(
  restaurant: Partial<RestaurantDetails> = {},
  owner: Partial<RestaurantOwner> = {},
): AdminRestaurantDetails {
  return {
    restaurant: restaurantDetails(restaurant),
    owner: {
      userId: 20,
      lookupStatus: 'FOUND',
      fullName: 'Ravi Kumar',
      email: 'ravi@example.com',
      phone: '+91 99000 11111',
      accountStatus: 'ACTIVE',
      ...owner,
    },
  };
}

export function category(id: number, name: string, overrides: Partial<Category> = {}): Category {
  return {
    id,
    name,
    description: null,
    imageUrl: null,
    displayOrder: id,
    status: 'ACTIVE',
    menuItemCount: 0,
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-09-01T09:00:00Z',
    version: 0,
    ...overrides,
  };
}

export function coupon(overrides: Partial<Coupon> = {}): Coupon {
  return {
    id: 5,
    code: 'WELCOME100',
    description: '100 off your first order',
    discountType: 'FIXED',
    discountValue: 100,
    maxDiscount: null,
    minOrderAmount: 499,
    summary: '100.00 off',
    restaurantId: null,
    validFrom: '2026-10-01',
    validUntil: '2026-10-31',
    usageLimit: 1000,
    perCustomerLimit: 1,
    timesUsed: 12,
    status: 'ACTIVE',
    availability: 'AVAILABLE',
    createdBy: 1,
    createdAt: '2026-09-30T09:00:00Z',
    updatedAt: '2026-09-30T09:00:00Z',
    version: 4,
    ...overrides,
  };
}

export function couponDetails(overrides: Partial<Coupon> = {}): CouponDetails {
  return {
    coupon: coupon(overrides),
    usesByStatus: { RESERVED: 1, APPLIED: 3, REDEEMED: 8, RELEASED: 2, EXPIRED: 0 },
    discountGiven: 1100,
    customers: 11,
  };
}

export function payment(overrides: Partial<Payment> = {}): Payment {
  return {
    id: 77,
    orderId: 42,
    customerId: 7,
    amount: 562.9,
    refundedAmount: 0,
    currency: 'INR',
    method: 'UPI',
    status: 'SUCCESS',
    instrument: 'priya@okbank',
    providerReference: 'SIM-123',
    failureReason: null,
    createdAt: '2026-10-08T10:15:00Z',
    processingStartedAt: '2026-10-08T10:15:01Z',
    completedAt: '2026-10-08T10:15:02Z',
    provider: 'SIMULATED',
    providerOrderId: null,
    ...overrides,
  };
}

export function refund(overrides: Partial<Refund> = {}): Refund {
  return {
    id: 9,
    paymentId: 77,
    orderId: 42,
    amount: 100,
    reason: 'Item missing',
    status: 'SUCCESS',
    requestedByType: 'ADMIN',
    requestedByUserId: 1,
    providerReference: 'SIM-R-9',
    failureReason: null,
    requestedAt: '2026-10-08T11:00:00Z',
    completedAt: '2026-10-08T11:00:01Z',
    ...overrides,
  };
}

export function menuItem(overrides: Partial<MenuItem> = {}): MenuItem {
  return {
    id: 11,
    restaurantId: 3,
    categoryId: 3,
    categoryName: 'Biryani',
    name: 'Chicken Biryani',
    description: 'Slow-cooked with saffron',
    price: 299,
    foodType: 'NON_VEG',
    imageUrl: null,
    available: true,
    status: 'ACTIVE',
    createdAt: '2026-09-01T09:00:00Z',
    updatedAt: '2026-10-01T09:00:00Z',
    version: 2,
    ...overrides,
  };
}

export function partner(overrides: Partial<Partner> = {}): Partner {
  return {
    userId: 31,
    fullName: 'Ravi Kumar',
    phone: '+919800000031',
    vehicleType: 'SCOOTER',
    vehicleNumber: 'KA01AB1234',
    city: 'Bengaluru',
    status: 'AVAILABLE',
    statusReason: null,
    currentDeliveryId: null,
    availableSince: '2026-10-08T09:00:00Z',
    completedDeliveries: 120,
    readyToWork: true,
    createdAt: '2026-08-01T09:00:00Z',
    updatedAt: '2026-10-08T09:00:00Z',
    version: 3,
    ...overrides,
  };
}

export function deliverySummary(overrides: Partial<DeliverySummary> = {}): DeliverySummary {
  return {
    id: 55,
    orderId: 42,
    status: 'ACCEPTED',
    restaurantName: 'Spice Garden',
    pickupCity: 'Bengaluru',
    dropoffCity: 'Bengaluru',
    partnerUserId: 31,
    partnerName: 'Ravi Kumar',
    cashToCollect: 0,
    readyAt: '2026-10-08T10:30:00Z',
    assignedAt: '2026-10-08T10:31:00Z',
    deliveredAt: null,
    minutesSinceReady: 12,
    ...overrides,
  };
}

export function delivery(overrides: Partial<Delivery> = {}): Delivery {
  const address = {
    line1: '12 MG Road',
    line2: null,
    city: 'Bengaluru',
    state: 'KA',
    postalCode: '560001',
    country: 'IN',
  };
  return {
    id: 55,
    orderId: 42,
    status: 'ACCEPTED',
    restaurantId: 3,
    restaurantName: 'Spice Garden',
    pickupAddress: address,
    customerId: 7,
    customerName: 'Priya Sharma',
    customerPhone: '+919800000007',
    dropoffAddress: { ...address, line1: '4 Church Street' },
    paymentMethod: 'UPI',
    cashToCollect: 0,
    cashCollected: null,
    currency: 'INR',
    partnerUserId: 31,
    partnerName: 'Ravi Kumar',
    vehicleType: 'SCOOTER',
    cancellationReason: null,
    readyAt: '2026-10-08T10:30:00Z',
    assignedAt: '2026-10-08T10:31:00Z',
    acceptedAt: '2026-10-08T10:32:00Z',
    pickedUpAt: null,
    deliveredAt: null,
    cancelledAt: null,
    createdAt: '2026-10-08T10:30:00Z',
    updatedAt: '2026-10-08T10:32:00Z',
    version: 2,
    timeline: [
      {
        event: 'CREATED',
        status: 'PENDING',
        partnerUserId: null,
        actorType: 'SYSTEM',
        actorUserId: null,
        note: null,
        at: '2026-10-08T10:30:00Z',
      },
      {
        event: 'ASSIGNED',
        status: 'ASSIGNED',
        partnerUserId: 31,
        actorType: 'SYSTEM',
        actorUserId: null,
        note: null,
        at: '2026-10-08T10:31:00Z',
      },
      {
        event: 'ACCEPTED',
        status: 'ACCEPTED',
        partnerUserId: 31,
        actorType: 'DELIVERY_PARTNER',
        actorUserId: 31,
        note: null,
        at: '2026-10-08T10:32:00Z',
      },
    ],
    ...overrides,
  };
}

/** The signed-in customer of the customer-area tests. */
export const priya = {
  id: 7,
  role: 'CUSTOMER' as const,
  fullName: 'Priya Sharma',
  email: 'priya@example.com',
};

/** A cart with 2 Chicken Biryani from restaurant 3 (598.00: delivery 40.00, tax 29.90). */
export function cart(overrides: Partial<Cart> = {}): Cart {
  return {
    restaurantId: 3,
    items: [
      {
        menuItemId: 11,
        name: 'Chicken Biryani',
        foodType: 'NON_VEG',
        unitPrice: 299,
        quantity: 2,
        lineTotal: 598,
        orderable: true,
      },
    ],
    coupon: null,
    price: {
      itemsTotal: 598,
      discountAmount: 0,
      deliveryFee: 0,
      taxAmount: 29.9,
      totalAmount: 627.9,
      currency: 'INR',
    },
    orderable: true,
    problems: [],
    ...overrides,
  };
}

export const emptyCart: Cart = {
  restaurantId: null,
  items: [],
  coupon: null,
  price: {
    itemsTotal: 0,
    discountAmount: 0,
    deliveryFee: 0,
    taxAmount: 0,
    totalAmount: 0,
    currency: 'INR',
  },
  orderable: false,
  problems: [],
};

export function address(overrides: Partial<Address> = {}): Address {
  return {
    id: 21,
    type: 'HOME',
    line1: '12 MG Road',
    line2: null,
    city: 'Bengaluru',
    state: 'Karnataka',
    postalCode: '560001',
    country: 'India',
    latitude: null,
    longitude: null,
    defaultAddress: true,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  };
}
/** Answers of the APIs, shaped like the real ones, for tests. */

export const adminProfile: UserProfile = {
  id: 1,
  email: 'admin@fooddelivery.local',
  fullName: 'Platform Administrator',
  phone: null,
  role: 'PLATFORM_ADMIN',
  status: 'ACTIVE',
  createdAt: '2026-09-01T10:00:00Z',
  lastLoginAt: null,
};

export function dashboardResponse(overrides: Partial<DashboardResponse> = {}): DashboardResponse {
  return {
    today: '2026-10-08',
    chartsFrom: '2026-10-02',
    currency: 'INR',
    generatedAt: '2026-10-08T12:00:00Z',
    summary: {
      totalCustomers: 176,
      totalRestaurants: 198,
      activeRestaurants: 152,
      inactiveRestaurants: 46,
      pendingRestaurantApprovals: 0,
      totalMenuItems: 243,
      activeMenuItems: 228,
      todaysOrders: 181,
      todaysCompletedOrders: 37,
      todaysCancelledOrders: 87,
      pendingOrders: 75,
      todaysRevenue: 15405.55,
      totalRevenue: 18591.1,
      todaysSuccessfulPayments: 78,
      todaysFailedPayments: 10,
      activeDeliveryPartners: 38,
      pendingDeliveryAssignments: 9,
    },
    charts: {
      ordersPerDay: [
        { date: '2026-10-07', orders: 100, completed: 20, cancelled: 50, revenue: 3000 },
        { date: '2026-10-08', orders: 200, completed: 30, cancelled: 100, revenue: 15591.1 },
      ],
      ordersByStatus: { PLACED: 53, DELIVERED: 46, CANCELLED: 144 },
      paymentOutcome: { successful: 87, failed: 13, refunded: 5 },
      restaurantPerformance: [
        {
          restaurantId: 130,
          restaurantName: 'Rider Kitchen',
          orders: 4,
          completed: 3,
          revenue: 1061.85,
          cancellationRate: 25,
        },
      ],
      deliveryPerformance: null,
    },
    unavailable: [],
    ...overrides,
  };
}

/** The answer of the order-activity API for one chosen day, week or month. */
export function orderActivityResponse(
  overrides: Partial<OrderActivityResponse> = {},
): OrderActivityResponse {
  return {
    fromDate: '2026-10-05',
    toDate: '2026-10-08',
    currency: 'INR',
    ordersPerDay: [
      { date: '2026-10-05', orders: 10, completed: 5, cancelled: 1, revenue: 500 },
      { date: '2026-10-06', orders: 0, completed: 0, cancelled: 0, revenue: 0 },
      { date: '2026-10-07', orders: 10, completed: 3, cancelled: 3, revenue: 300 },
      { date: '2026-10-08', orders: 0, completed: 0, cancelled: 0, revenue: 0 },
    ],
    paymentOutcome: { successful: 9, failed: 1, refunded: 0 },
    unavailable: [],
    ...overrides,
  };
}

export const monthlyRevenue: MonthlyRevenueComparison = {
  currency: 'INR',
  thisYear: 2026,
  lastYear: 2025,
  months: [{ month: 'Oct', thisYear: 18591.1, lastYear: 0 }],
  unavailable: [],
};

export function review(overrides: Partial<Review> = {}): Review {
  return {
    id: 1,
    orderId: 42,
    customerId: 7,
    customerName: 'James Kowalski',
    restaurantId: 3,
    restaurantName: 'Biryani House',
    rating: 4,
    comment: 'Great food and fast delivery.',
    tags: ['DELICIOUS', 'GOOD_SERVICE'],
    dishRatings: [{ menuItemId: 11, dishName: 'Chicken Biryani', rating: 5 }],
    createdAt: '2026-06-24T10:15:00Z',
    ...overrides,
  };
}

export function featuredDish(overrides: Partial<FeaturedDish> = {}): FeaturedDish {
  return {
    menuItemId: 11,
    dishName: 'Chicken Biryani',
    imageUrl: null,
    categoryName: 'Main course',
    restaurantId: 3,
    restaurantName: 'Biryani House',
    averageRating: 4.5,
    ratingCount: 12,
    topReview: {
      reviewId: 1,
      customerName: 'Roberto Jr.',
      comment: 'Best biryani in town.',
      rating: 5,
      createdAt: '2026-06-24T10:15:00Z',
    },
    ...overrides,
  };
}

export function reviewSummary(overrides: Partial<ReviewSummary> = {}): ReviewSummary {
  return {
    count: 10,
    averageRating: 4.2,
    distribution: { '1': 0, '2': 1, '3': 1, '4': 4, '5': 4 },
    ...overrides,
  };
}

export function restaurantSummary(overrides: Partial<RestaurantSummary> = {}): RestaurantSummary {
  return {
    id: 3,
    name: 'Biryani House',
    ownerUserId: 21,
    cuisines: ['Indian'],
    city: 'Bengaluru',
    status: 'ACTIVE',
    statusReason: null,
    createdAt: '2026-08-01T09:00:00Z',
    statusChangedAt: '2026-08-02T09:00:00Z',
    ...overrides,
  };
}

export function categoryStat(overrides: Partial<CategoryStat> = {}): CategoryStat {
  return {
    categoryId: 3,
    categoryName: 'Biryani',
    totalItems: 8,
    orderableItems: 6,
    orderablePercent: 75,
    ...overrides,
  };
}

export function dishRevenue(overrides: Partial<DishRevenue> = {}): DishRevenue {
  return {
    menuItemId: 11,
    period: 'MONTHLY',
    totalRevenue: 59800,
    totalQuantity: 200,
    points: [
      { label: 'Sep', start: '2026-09-01', revenue: 29900, quantity: 100, orders: 60 },
      { label: 'Oct', start: '2026-10-01', revenue: 29900, quantity: 100, orders: 55 },
    ],
    ...overrides,
  };
}

export function customerTopFood(overrides: Partial<CustomerTopFood> = {}): CustomerTopFood {
  return {
    menuItemId: 11,
    name: 'Chicken Biryani',
    orders: 4,
    quantity: 6,
    totalSpent: 1800,
    unitPrice: 300,
    lastOrderedAt: '2026-10-07T12:00:00Z',
    imageUrl: null,
    categoryName: 'Biryani',
    ...overrides,
  };
}

export function likedDish(overrides: Partial<LikedDish> = {}): LikedDish {
  return {
    menuItemId: 11,
    dishName: 'Chicken Biryani',
    averageRating: 4.5,
    ratingCount: 6,
    lastRatedAt: '2026-10-05T12:00:00Z',
    ...overrides,
  };
}

/** A calendar event (a single day of placed orders by default). */
export function calendarEvent(overrides: Partial<CalendarEvent> = {}): CalendarEvent {
  return {
    id: 'orders-placed-2026-10-09',
    type: 'ORDERS',
    kind: 'ORDERS_PLACED',
    title: '12 orders',
    start: '2026-10-09',
    end: '2026-10-09',
    count: 12,
    status: null,
    tone: 'info',
    ref: null,
    ...overrides,
  };
}

/** A coupon campaign: bar events of kind COUPON. */
export function couponEvent(overrides: Partial<CalendarEvent> = {}): CalendarEvent {
  return calendarEvent({
    id: 'coupon-7',
    type: 'COUPONS',
    kind: 'COUPON',
    title: 'SAVE20',
    start: '2026-10-08',
    end: '2026-10-14',
    count: 5,
    status: 'ACTIVE',
    tone: 'success',
    ref: { couponId: 7 },
    ...overrides,
  });
}

export function calendarResponse(overrides: Partial<CalendarResponse> = {}): CalendarResponse {
  return {
    from: '2026-09-27',
    to: '2026-11-07',
    timeZone: 'Asia/Kolkata',
    unavailable: [],
    events: [],
    ...overrides,
  };
}

export function chatConversation(overrides: Partial<ChatConversation> = {}): ChatConversation {
  return {
    id: 5,
    participant: { userId: 7, name: 'Priya Sharma', role: 'CUSTOMER', online: null },
    lastMessage: {
      preview: 'Where is my order?',
      at: '2026-10-08T10:15:00Z',
      fromStaff: false,
      read: false,
    },
    unreadCount: 0,
    pinned: false,
    muted: false,
    createdAt: '2026-10-01T09:00:00Z',
    ...overrides,
  };
}

export function chatMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  const id = overrides.id ?? 1;
  return {
    id,
    conversationId: 5,
    clientMessageId: `client-${id}`,
    senderUserId: 7,
    senderName: 'Priya Sharma',
    senderRole: 'CUSTOMER',
    fromStaff: false,
    body: `Message ${id}`,
    createdAt: '2026-10-08T10:15:00Z',
    readAt: null,
    ...overrides,
  };
}

export function chatMessagePage(
  messages: ChatMessage[],
  hasMore = false,
  nextBeforeId: number | null = null,
): ChatMessagePage {
  return { messages, hasMore, nextBeforeId };
}
