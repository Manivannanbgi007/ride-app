import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { couponService } from '@/services/couponService';
import type {
  Coupon,
  CouponDetails,
  CouponFilters,
  CreateCouponRequest,
  RedemptionStatus,
  UpdateCouponRequest,
} from '@/types/coupon';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** One page of coupons. While the next page loads, the current one stays on screen. */
export function useCoupons(filters: CouponFilters, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.coupons.list(filters, page, size),
    queryFn: () => couponService.searchCoupons(filters, page, size),
    placeholderData: keepPreviousData,
  });
}

export function useCoupon(couponId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.coupons.details(couponId),
    queryFn: () => couponService.getCoupon(couponId),
  });
}

export function useCouponRedemptions(
  couponId: number,
  status: RedemptionStatus | undefined,
  page: number,
  size: number,
) {
  return useQuery({
    queryKey: QUERY_KEYS.coupons.redemptions(couponId, status, page),
    queryFn: () => couponService.getRedemptions(couponId, status, page, size),
    placeholderData: keepPreviousData,
  });
}

export function useCouponAuditLogs(couponId: number) {
  return useQuery({
    queryKey: QUERY_KEYS.coupons.auditLogs(couponId),
    queryFn: () => couponService.getAuditLogs(couponId),
  });
}

/** After a change: show the new terms at once (keeping the usage figures), then reload lists and history. */
function useCouponChanged() {
  const queryClient = useQueryClient();
  return async (coupon: Coupon) => {
    queryClient.setQueryData<CouponDetails>(QUERY_KEYS.coupons.details(coupon.id), (current) =>
      current ? { ...current, coupon } : current,
    );
    await queryClient.invalidateQueries({ queryKey: QUERY_KEYS.coupons.all });
  };
}

export function useCreateCoupon() {
  const onSuccess = useCouponChanged();
  return useMutation({
    mutationFn: (coupon: CreateCouponRequest) => couponService.createCoupon(coupon),
    onSuccess,
  });
}

export function useUpdateCoupon() {
  const onSuccess = useCouponChanged();
  return useMutation({
    mutationFn: ({ couponId, coupon }: { couponId: number; coupon: UpdateCouponRequest }) =>
      couponService.updateCoupon(couponId, coupon),
    onSuccess,
  });
}

export function useChangeCouponStatus() {
  const onSuccess = useCouponChanged();
  return useMutation({
    mutationFn: ({
      couponId,
      activate,
      reason,
    }: {
      couponId: number;
      activate: boolean;
      reason: string;
    }) => couponService.changeStatus(couponId, activate, reason),
    onSuccess,
  });
}

/** Public: the offers customers can use today (with a restaurant's own when restaurantId is given). */
export function useOffers(restaurantId: number | null) {
  return useQuery({
    queryKey: QUERY_KEYS.storefront.offers(restaurantId),
    queryFn: () => couponService.getOffers(restaurantId),
    staleTime: 60_000,
  });
}
