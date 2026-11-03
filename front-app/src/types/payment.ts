import type { OrderStatus, PaymentMethod } from '@/types/order';

/** payment-service (admin): payments and refunds. */

/** An admin refund needs the order to be finished one way or another (payment-service checks it again). */
export const REFUNDABLE_ORDER_STATUSES: readonly OrderStatus[] = [
  'CANCELLED',
  'REJECTED',
  'DELIVERED',
];

/**
 * A payment's status in payment-service: INITIATED -> PROCESSING -> SUCCESS or FAILED; SUCCESS -> REFUNDED when
 * fully refunded (partly refunded stays SUCCESS). Not the same list as an order's paymentStatus in order-service
 * (PENDING, PAID, FAILED, REFUNDED).
 */
export const PAYMENT_RECORD_STATUSES = [
  'INITIATED',
  'PROCESSING',
  'SUCCESS',
  'FAILED',
  'REFUNDED',
] as const;
export type PaymentRecordStatus = (typeof PAYMENT_RECORD_STATUSES)[number];

/** REQUESTED -> PROCESSING -> SUCCESS or FAILED. */
export const REFUND_STATUSES = ['REQUESTED', 'PROCESSING', 'SUCCESS', 'FAILED'] as const;
export type RefundStatus = (typeof REFUND_STATUSES)[number];

/** Who asked for a refund: an admin, or the system (e.g. a payment for an order already cancelled). */
export type RequesterType = 'ADMIN' | 'SYSTEM';

/** A payment (PaymentResponse). */
export interface Payment {
  id: number;
  orderId: number;
  customerId: number;
  amount: number;
  /** Refunds that succeeded or are still running */
  refundedAmount: number;
  currency: string;
  method: PaymentMethod;
  status: PaymentRecordStatus;
  /** Masked: "VISA **** 1111" */
  instrument: string | null;
  providerReference: string | null;
  failureReason: string | null;
  createdAt: string;
  processingStartedAt: string | null;
  completedAt: string | null;
  /** The gateway that took the payment */
  provider: PaymentProvider;
  /** Razorpay only: the Razorpay order ("order_...") */
  providerOrderId: string | null;
}

/** SIMULATED: payment-service's built-in test gateway; RAZORPAY: Razorpay Checkout. */
export type PaymentProvider = 'SIMULATED' | 'RAZORPAY';

/** GET /api/v1/payments/razorpay/config: whether online payments use Razorpay Checkout here. */
export interface RazorpayConfig {
  enabled: boolean;
  /** The public key id (rzp_test_... in Test Mode); the key secret never reaches the browser */
  keyId: string | null;
  /** Test Mode keys: no real money moves */
  testMode: boolean;
}

/** POST /api/v1/payments/razorpay/orders: what Razorpay Checkout is opened with. */
export interface RazorpayCheckoutSession {
  /** Our payment (PROCESSING while the customer pays in Checkout) */
  paymentId: number;
  orderId: number;
  razorpayOrderId: string;
  keyId: string;
  /** In paise (₹353.95 = 35395), as Checkout expects; set by the server from the order's total */
  amount: number;
  currency: string;
  merchantName: string;
  description: string;
  /** card, upi or wallet: the method chosen at checkout */
  preferredMethod: string;
}

/** POST /api/v1/payments/razorpay/verify: the three values Checkout's handler received, passed on unchanged. */
export interface RazorpayVerifyRequest {
  razorpayOrderId: string;
  razorpayPaymentId: string;
  razorpaySignature: string;
}

/** A refund (RefundResponse). */
export interface Refund {
  id: number;
  paymentId: number;
  orderId: number;
  amount: number;
  reason: string;
  status: RefundStatus;
  requestedByType: RequesterType;
  /** null for SYSTEM refunds */
  requestedByUserId: number | null;
  providerReference: string | null;
  failureReason: string | null;
  requestedAt: string;
  completedAt: string | null;
}

/**
 * An event of the payment in payment-service's outbox: PENDING while it waits for Kafka, PUBLISHED once sent
 * (e.g. whether order-service was told the payment succeeded).
 */
export interface PublishedEvent {
  eventId: string;
  eventType: string;
  topic: string;
  status: 'PENDING' | 'PUBLISHED';
  createdAt: string;
  publishedAt: string | null;
  attempts: number;
  lastError: string | null;
}

/** GET /api/v1/admin/payments/{id}: the payment, its refunds (newest first) and its events. */
export interface PaymentDetails {
  payment: Payment;
  refunds: Refund[];
  events: PublishedEvent[];
}

/** Payment monitoring for a period (PaymentSummaryResponse); statuses and methods without payments are left out. */
export interface PaymentSummary {
  fromDate: string;
  toDate: string;
  byStatus: Partial<Record<PaymentRecordStatus, { count: number; amount: number }>>;
  byMethod: Partial<Record<PaymentMethod, { count: number; amount: number }>>;
  /** Successful and refunded payments, before refunds */
  collectedAmount: number;
  /** Refunds done or still running */
  refundedAmount: number;
  currency: string;
}

/** The payment list's filters (dates: created, 'YYYY-MM-DD', inclusive). */
export interface PaymentFilters {
  paymentId?: number;
  orderId?: number;
  customerId?: number;
  status?: PaymentRecordStatus;
  method?: PaymentMethod;
  fromDate?: string;
  toDate?: string;
}

/** The refund list's filters (dates: requested, inclusive). */
export interface RefundFilters {
  paymentId?: number;
  orderId?: number;
  status?: RefundStatus;
  fromDate?: string;
  toDate?: string;
}
