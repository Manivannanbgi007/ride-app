/** review-service: customers rate delivered orders (the order, its dishes), admins read the reviews. */

export const REVIEW_TAGS = [
  'DELICIOUS',
  'EXCELLENT',
  'GOOD_SERVICE',
  'FAST_DELIVERY',
  'GOOD_VALUE',
  'GOOD_PACKAGING',
] as const;
export type ReviewTag = (typeof REVIEW_TAGS)[number];

export interface DishRating {
  menuItemId: number;
  dishName: string;
  rating: number;
}

export interface Review {
  id: number;
  orderId: number;
  customerId: number;
  customerName: string;
  restaurantId: number;
  restaurantName: string;
  rating: number;
  comment: string | null;
  tags: ReviewTag[];
  dishRatings: DishRating[];
  /** ISO-8601 instant */
  createdAt: string;
  /** ISO-8601 instant of the last change */
  updatedAt?: string;
  /** Sent back when changing the review, so a change made from another screen is not overwritten */
  version?: number;
}

/** Changing a review: the same fields as writing it (the dish ratings are replaced as a whole). */
export type UpdateReviewRequest = Omit<CreateReviewRequest, 'orderId'> & { version?: number };

export interface CreateReviewRequest {
  orderId: number;
  rating: number;
  comment?: string;
  tags?: ReviewTag[];
  dishRatings?: { menuItemId: number; rating: number }[];
}

/** createdAt,desc | createdAt,asc | rating,desc | rating,asc */
export type ReviewSort = 'createdAt,desc' | 'createdAt,asc' | 'rating,desc' | 'rating,asc';

/** The period ('YYYY-MM-DD', both days included) and restaurant the admin looks at. */
export interface ReviewScope {
  restaurantId?: number;
  /** Only reviews of this dish (the numbers are then the dish's own ratings) */
  menuItemId?: number;
  from?: string;
  to?: string;
}

export interface ReviewFilters extends ReviewScope {
  rating?: number;
  tag?: ReviewTag;
  sort: ReviewSort;
}

export interface ReviewSummary {
  count: number;
  averageRating: number | null;
  /** "1" to "5": how many reviews gave that rating */
  distribution: Record<string, number>;
}

export interface FeaturedDish {
  menuItemId: number;
  dishName: string;
  imageUrl: string | null;
  categoryName: string | null;
  restaurantId: number;
  restaurantName: string;
  averageRating: number;
  ratingCount: number;
  topReview: {
    reviewId: number;
    customerName: string;
    comment: string | null;
    rating: number;
    createdAt: string;
  } | null;
}

/** The window of a customer's liked dishes. Ratings are sparse, so "all time" replaces a daily view. */
export const LIKED_DISHES_PERIODS = ['ALL', 'MONTHLY', 'WEEKLY'] as const;
export type LikedDishesPeriod = (typeof LIKED_DISHES_PERIODS)[number];

export interface LikedDish {
  menuItemId: number;
  dishName: string;
  /** 0 to 5 */
  averageRating: number;
  ratingCount: number;
  /** ISO-8601 instant */
  lastRatedAt: string;
}

/** The dishes a customer rated highest; `ratingCount` is all their dish ratings in the period. */
export interface CustomerLikedDishes {
  customerId: number;
  period: LikedDishesPeriod;
  ratingCount: number;
  dishes: LikedDish[];
}
