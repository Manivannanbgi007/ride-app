import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type {
  CreateReviewRequest,
  CustomerLikedDishes,
  FeaturedDish,
  LikedDishesPeriod,
  Review,
  ReviewFilters,
  ReviewScope,
  ReviewSummary,
  UpdateReviewRequest,
} from '@/types/review';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** review-service APIs: customers' reviews of delivered orders, and the admin's view of them. */
export const reviewService = {
  /** One page of reviews (page 0 = the first). */
  searchReviews: (filters: ReviewFilters, page: number, size: number) =>
    apiClient.get<PageResponse<Review>>(API_ENDPOINTS.admin.reviews.list, {
      params: { ...filters, page, size },
    }),

  /** How many reviews, their average and how many gave 1 to 5 stars. */
  getSummary: (scope: ReviewScope) =>
    apiClient.get<ReviewSummary>(API_ENDPOINTS.admin.reviews.summary, { params: { ...scope } }),

  /** The best rated dishes, with their top review (page 0 = the first). */
  getFeaturedDishes: (scope: ReviewScope, page: number, size: number) =>
    apiClient.get<PageResponse<FeaturedDish>>(API_ENDPOINTS.admin.reviews.featuredDishes, {
      params: { ...scope, page, size },
    }),

  /** The signed-in customer's review of a delivered order (409 when the order is already reviewed). */
  createReview: (review: CreateReviewRequest) =>
    apiClient.post<Review>(API_ENDPOINTS.reviews.create, review),

  /** Changes the customer's own review (someone else's is 404). */
  updateReview: (reviewId: number, review: UpdateReviewRequest) =>
    apiClient.put<Review>(API_ENDPOINTS.reviews.review(reviewId), review),

  /** Deletes the customer's own review (someone else's is 404); the order can be reviewed again. */
  deleteReview: (reviewId: number) =>
    apiClient.delete<void>(API_ENDPOINTS.reviews.review(reviewId)),

  /** The review of an order; 404 while the customer has not written one. */
  getOrderReview: (orderId: number) =>
    apiClient.get<Review>(API_ENDPOINTS.reviews.ofOrder(orderId)),

  /** The dishes a customer rated highest (their average rating per dish), best first. */
  getCustomerLikedDishes: (customerId: number, period: LikedDishesPeriod, limit: number) =>
    apiClient.get<CustomerLikedDishes>(
      API_ENDPOINTS.admin.reviews.customerLikedDishes(customerId),
      {
        params: { period, limit },
      },
    ),
};
