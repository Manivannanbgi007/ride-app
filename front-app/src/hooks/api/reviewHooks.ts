import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';

import { toApiError } from '@/api/apiErrorHandler';
import { useCustomerId } from '@/hooks/api/cartHooks';
import { reviewService } from '@/services/reviewService';
import type {
  CreateReviewRequest,
  LikedDishesPeriod,
  ReviewFilters,
  ReviewScope,
  UpdateReviewRequest,
} from '@/types/review';
import { QUERY_KEYS } from '@/utils/queryKeys';

/** The reviews, a page more each time `fetchNextPage` is called (the earlier pages stay in `data.pages`). */
export function useReviews(filters: ReviewFilters, size: number) {
  return useInfiniteQuery({
    queryKey: QUERY_KEYS.reviews.list(filters, size),
    queryFn: ({ pageParam }) => reviewService.searchReviews(filters, pageParam, size),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.last ? undefined : last.page + 1),
  });
}

export function useReviewSummary(scope: ReviewScope) {
  return useQuery({
    queryKey: QUERY_KEYS.reviews.summary(scope),
    queryFn: () => reviewService.getSummary(scope),
  });
}

/** One page of featured dishes; while the next page loads, the current one stays on screen. */
export function useFeaturedDishes(scope: ReviewScope, page: number, size: number) {
  return useQuery({
    queryKey: QUERY_KEYS.reviews.featured(scope, page, size),
    queryFn: () => reviewService.getFeaturedDishes(scope, page, size),
    placeholderData: keepPreviousData,
  });
}

/** The signed-in customer's review of an order: null while they have not written one (404). */
export function useOrderReview(orderId: number, enabled: boolean) {
  const customerId = useCustomerId();
  return useQuery({
    queryKey: QUERY_KEYS.orderReview(customerId, orderId),
    queryFn: async () => {
      try {
        return await reviewService.getOrderReview(orderId);
      } catch (error) {
        if (toApiError(error).kind === 'notFound') {
          return null;
        }
        throw error;
      }
    },
    enabled: enabled && customerId > 0,
  });
}

/** Rating an order: the review is shown at once, and the admin's figures reload. */
export function useCreateReview() {
  const customerId = useCustomerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (review: CreateReviewRequest) => reviewService.createReview(review),
    onSuccess: async (review) => {
      queryClient.setQueryData(QUERY_KEYS.orderReview(customerId, review.orderId), review);
      await refreshAfterReviewChange(queryClient);
    },
  });
}

/** Changing the customer's own review: it is shown at once, and every rating built from it reloads. */
export function useUpdateReview() {
  const customerId = useCustomerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reviewId, review }: { reviewId: number; review: UpdateReviewRequest }) =>
      reviewService.updateReview(reviewId, review),
    onSuccess: async (review) => {
      queryClient.setQueryData(QUERY_KEYS.orderReview(customerId, review.orderId), review);
      await refreshAfterReviewChange(queryClient);
    },
  });
}

/** Deleting the customer's own review: the order shows "Rate this order" again. */
export function useDeleteReview() {
  const customerId = useCustomerId();
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reviewId }: { reviewId: number; orderId: number }) =>
      reviewService.deleteReview(reviewId),
    onSuccess: async (_, { orderId }) => {
      queryClient.setQueryData(QUERY_KEYS.orderReview(customerId, orderId), null);
      await refreshAfterReviewChange(queryClient);
    },
  });
}

/** The admin's figures and the public ratings (cards, lists, a restaurant's reviews) are built from reviews. */
async function refreshAfterReviewChange(queryClient: QueryClient): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.reviews.all }),
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.ratings.all }),
  ]);
}

/** The dishes a customer rated highest, over all time, the last month or the last week. */
export function useCustomerLikedDishes(
  customerId: number,
  period: LikedDishesPeriod,
  limit: number,
) {
  return useQuery({
    queryKey: QUERY_KEYS.reviews.customerLikedDishes(customerId, period, limit),
    queryFn: () => reviewService.getCustomerLikedDishes(customerId, period, limit),
  });
}
