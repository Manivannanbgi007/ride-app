import { apiClient } from '@/api/apiClient';
import type { AuditLogEntry, PageResponse } from '@/types/api';
import type {
  Coupon,
  CouponDetails,
  CouponFilters,
  CreateCouponRequest,
  Redemption,
  RedemptionStatus,
  UpdateCouponRequest,
} from '@/types/coupon';
import type { Offer } from '@/types/shopping';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** promotion-service APIs: coupons (admin) and the offers customers can use. */
export const couponService = {
  /** Public: platform-wide coupons usable today, plus a restaurant's own when restaurantId is given. */
  getOffers: (restaurantId: number | null) =>
    apiClient.get<Offer[]>(API_ENDPOINTS.storefront.offers, {
      params: { restaurantId: restaurantId ?? undefined },
    }),

  searchCoupons: (filters: CouponFilters, page: number, size: number) =>
    apiClient.get<PageResponse<Coupon>>(API_ENDPOINTS.admin.coupons.list, {
      params: { ...filters, page, size },
    }),

  /** The coupon with its usage: uses per status, discount given, customers. */
  getCoupon: (couponId: number) =>
    apiClient.get<CouponDetails>(API_ENDPOINTS.admin.coupons.details(couponId)),

  /** A new coupon is ACTIVE at once (409 when the code exists). */
  createCoupon: (coupon: CreateCouponRequest) =>
    apiClient.post<Coupon>(API_ENDPOINTS.admin.coupons.create, coupon),

  /**
   * New terms (the code never changes; placed orders keep their discount). Send the version you read: someone
   * else's change in between gives 409.
   */
  updateCoupon: (couponId: number, coupon: UpdateCouponRequest) =>
    apiClient.put<Coupon>(API_ENDPOINTS.admin.coupons.update(couponId), coupon),

  /** Pause (no new uses from now on) or resume a coupon. Audited. */
  changeStatus: (couponId: number, activate: boolean, reason: string) =>
    apiClient.put<Coupon>(
      activate
        ? API_ENDPOINTS.admin.coupons.activate(couponId)
        : API_ENDPOINTS.admin.coupons.deactivate(couponId),
      { reason },
    ),

  /** The uses of a coupon, newest first. */
  getRedemptions: (
    couponId: number,
    status: RedemptionStatus | undefined,
    page: number,
    size: number,
  ) =>
    apiClient.get<PageResponse<Redemption>>(API_ENDPOINTS.admin.coupons.redemptions(couponId), {
      params: { status, page, size },
    }),

  getAuditLogs: (couponId: number) =>
    apiClient.get<PageResponse<AuditLogEntry>>(API_ENDPOINTS.admin.coupons.auditLogs(couponId), {
      params: { size: 50 },
    }),
};
