import { apiClient } from '@/api/apiClient';
import type { PageResponse } from '@/types/api';
import type { PublicReview, PublicReviewSort, RatingDetails, RatingSummary } from '@/types/rating';
import { API_ENDPOINTS } from '@/utils/apiEndpoints';

/** review-service's public ratings: what visitors and customers see on restaurants and dishes. */
export const ratingService = {
  /** The ratings of up to 100 restaurants at once; restaurants without reviews are not in the answer. */
  getRestaurantRatings: (ids: readonly number[]) =>
    apiClient.get<RatingSummary[]>(API_ENDPOINTS.ratings.restaurants, {
      params: { ids: ids.join(',') },
    }),

  /** The ratings of up to 100 dishes at once; dishes without ratings are not in the answer. */
  getDishRatings: (ids: readonly number[]) =>
    apiClient.get<RatingSummary[]>(API_ENDPOINTS.ratings.menuItems, {
      params: { ids: ids.join(',') },
    }),

  /** One restaurant: average, count and how many gave 1 to 5 stars (count 0 while nobody has rated it). */
  getRestaurantRating: (restaurantId: number) =>
    apiClient.get<RatingDetails>(API_ENDPOINTS.ratings.restaurant(restaurantId)),

  /** One dish: average, count and how many gave 1 to 5 stars. */
  getDishRating: (menuItemId: number) =>
    apiClient.get<RatingDetails>(API_ENDPOINTS.ratings.menuItem(menuItemId)),

  /** One page of a restaurant's reviews (page 0 = the first). */
  getRestaurantReviews: (
    restaurantId: number,
    sort: PublicReviewSort,
    page: number,
    size: number,
  ) =>
    apiClient.get<PageResponse<PublicReview>>(
      API_ENDPOINTS.ratings.restaurantReviews(restaurantId),
      {
        params: { sort, page, size },
      },
    ),

  /**
   * One page of the reviews that rated a dish, newest first: each customer's comment and tags on the order, and the
   * stars every rated dish got (the reviewer's name is shortened, as on the restaurant's reviews).
   */
  getDishReviews: (menuItemId: number, page: number, size: number) =>
    apiClient.get<PageResponse<PublicReview>>(API_ENDPOINTS.ratings.menuItemReviews(menuItemId), {
      params: { page, size },
    }),
};
