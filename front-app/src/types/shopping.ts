/**
 * What a signed-in customer buys with: the cart (order-service), their addresses (user-service), offers
 * (promotion-service), checkout (order-service) and paying (payment-service).
 */
import type { FoodType } from '@/types/catalog';
import type { DeliveryAddress, DeliveryStatus, VehicleType } from '@/types/delivery';
import type { OrderStatus, PaymentMethod, PriceBreakdown } from '@/types/order';

/** One dish in the cart. Name and prices are null when the dish no longer exists. */
export interface CartLine {
  menuItemId: number;
  name: string | null;
  foodType: FoodType | null;
  unitPrice: number | null;
  quantity: number;
  lineTotal: number | null;
  /** On the menu and available right now */
  orderable: boolean;
}

export interface CartCoupon {
  code: string;
  description: string | null;
  /** False when the coupon no longer applies (reason says why); it stays in the cart until removed */
  applied: boolean;
  discountAmount: number;
  reason: string | null;
}

/**
 * GET /api/v1/cart: one cart per customer, from one restaurant at a time. Prices are always today's. The cart
 * does not check whether the restaurant is open: checkout does.
 */
export interface Cart {
  restaurantId: number | null;
  items: CartLine[];
  coupon: CartCoupon | null;
  price: PriceBreakdown;
  /** Items, and no problems */
  orderable: boolean;
  /** "'Veg Thali' is not available right now", "Coupon SAVE50: ..." */
  problems: string[];
}

export const ADDRESS_TYPES = ['HOME', 'WORK', 'OTHER'] as const;
export type AddressType = (typeof ADDRESS_TYPES)[number];

/** A saved delivery address (exactly one is the default; at most 10). */
export interface Address {
  id: number;
  type: AddressType;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  latitude: number | null;
  longitude: number | null;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddressRequest {
  type: AddressType;
  line1: string;
  line2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  /** The map pin (both or neither); null removes it */
  latitude?: number | null;
  longitude?: number | null;
  makeDefault?: boolean;
}

/** GET /api/v1/coupons: a coupon customers can use today (their own earlier uses are not taken into account). */
export interface Offer {
  code: string;
  description: string | null;
  /** "100.00 off", "20% off, up to 150.00" */
  summary: string;
  minOrderAmount: number;
  /** Set: only at this restaurant */
  restaurantId: number | null;
  /** 'YYYY-MM-DD', the last day it can be used */
  validUntil: string;
}

/** POST /api/v1/orders: the cart becomes an order (the coupon applied to the cart comes with it). */
export interface PlaceOrderRequest {
  addressId: number;
  paymentMethod: PaymentMethod;
  notes?: string;
}

export type OnlinePaymentMethod = Exclude<PaymentMethod, 'CASH_ON_DELIVERY'>;

/** POST /api/v1/payments: the amount always comes from the order, never from the browser. */
export interface PayOrderRequest {
  orderId: number;
  paymentMethod: OnlinePaymentMethod;
  card?: { number: string; expiryMonth: number; expiryYear: number };
  upiId?: string;
  walletId?: string;
}

/** A customer may cancel an order only before the restaurant accepts it (order-service checks it too). */
export const CUSTOMER_CANCELLABLE_STATUSES: readonly OrderStatus[] = ['PAYMENT_PENDING', 'PLACED'];

/**
 * GET /api/v1/delivery/orders/{orderId}: the delivery of the customer's order, from the moment the food is ready
 * (404 before). The partner's name and vehicle are known once a partner accepts; their phone only while on the
 * way.
 */
export interface OrderTracking {
  orderId: number;
  deliveryId: number;
  status: DeliveryStatus;
  restaurantName: string | null;
  dropoffAddress: DeliveryAddress | null;
  partnerName: string | null;
  partnerPhone: string | null;
  vehicleType: VehicleType | null;
  /** Cash on delivery: the total; 0 for paid orders */
  cashToCollect: number;
  currency: string;
  readyAt: string;
  acceptedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  steps: TrackingStep[];
}

export interface TrackingStep {
  status: DeliveryStatus;
  /** "Ravi is on the way to the restaurant", "Picked up from Spice Garden" */
  note: string | null;
  at: string;
}
