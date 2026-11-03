/** promotion-service: coupons (admin). */

/** Set by admins: an INACTIVE coupon cannot be used, whatever its dates. */
export const COUPON_STATUSES = ['ACTIVE', 'INACTIVE'] as const;
export type CouponStatus = (typeof COUPON_STATUSES)[number];

/** PERCENTAGE: discountValue percent of the items, at most maxDiscount. FIXED: discountValue off. */
export const DISCOUNT_TYPES = ['PERCENTAGE', 'FIXED'] as const;
export type DiscountType = (typeof DISCOUNT_TYPES)[number];

/**
 * Whether a coupon can be used today, worked out by promotion-service from its status, dates and uses:
 * INACTIVE (paused), SCHEDULED (first day not reached), EXPIRED, USED_UP (usage limit reached), AVAILABLE.
 */
export type CouponAvailability = 'AVAILABLE' | 'SCHEDULED' | 'EXPIRED' | 'USED_UP' | 'INACTIVE';

/**
 * One use of a coupon: RESERVED at checkout, APPLIED once the order exists, REDEEMED when it is delivered;
 * RELEASED (order cancelled) or EXPIRED (checkout never finished) give the use back.
 */
export const REDEMPTION_STATUSES = [
  'RESERVED',
  'APPLIED',
  'REDEEMED',
  'RELEASED',
  'EXPIRED',
] as const;
export type RedemptionStatus = (typeof REDEMPTION_STATUSES)[number];

/** A coupon as admins see it (CouponResponse). Amounts in INR; dates 'YYYY-MM-DD', both inclusive. */
export interface Coupon {
  id: number;
  /** Upper case; cannot be changed */
  code: string;
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  minOrderAmount: number;
  /** The discount in words: "20% off, up to 150.00" */
  summary: string;
  /** null: every restaurant */
  restaurantId: number | null;
  validFrom: string;
  validUntil: string;
  /** null: unlimited */
  usageLimit: number | null;
  perCustomerLimit: number | null;
  /** Uses counting against the limit now (reserved, on an order, or redeemed) */
  timesUsed: number;
  status: CouponStatus;
  availability: CouponAvailability;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
  /** Send it back when changing the terms (someone else's change in between gives 409) */
  version: number;
}

/** A coupon with its usage (CouponDetailsResponse). */
export interface CouponDetails {
  coupon: Coupon;
  usesByStatus: Record<RedemptionStatus, number>;
  /** Discount on orders placed and not cancelled */
  discountGiven: number;
  /** Different customers who used it at least once */
  customers: number;
}

/** One use of a coupon (RedemptionResponse). */
export interface Redemption {
  id: number;
  couponCode: string;
  customerId: number;
  restaurantId: number;
  /** null while RESERVED */
  orderId: number | null;
  orderAmount: number;
  discountAmount: number;
  status: RedemptionStatus;
  releaseReason: string | null;
  reservedAt: string;
  appliedAt: string | null;
  redeemedAt: string | null;
  releasedAt: string | null;
}

/** The terms of a coupon, as sent when creating (with code) or changing it (with version). */
export interface CouponTerms {
  description: string | null;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount: number | null;
  minOrderAmount: number | null;
  restaurantId: number | null;
  validFrom: string;
  validUntil: string;
  usageLimit: number | null;
  perCustomerLimit: number | null;
}

export interface CreateCouponRequest extends CouponTerms {
  code: string;
}

export interface UpdateCouponRequest extends CouponTerms {
  version: number;
}

/** The coupon list's filters and order. */
export interface CouponFilters {
  /** Part of the code */
  code?: string;
  status?: CouponStatus;
  discountType?: DiscountType;
  restaurantId?: number;
  /** Valid on this day by its dates ('YYYY-MM-DD') */
  validOn?: string;
  sort: string;
}
