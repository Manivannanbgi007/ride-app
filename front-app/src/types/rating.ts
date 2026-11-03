import type { DishRating, ReviewTag } from '@/types/review';

/** GET /api/v1/ratings/restaurants?ids=... and /menu-items?ids=...: only ids that have reviews are listed. */
export interface RatingSummary {
  id: number;
  /** 0 to 5, one decimal */
  average: number;
  /** How many ratings it is made of (always at least 1 here) */
  count: number;
}

/** GET /api/v1/ratings/restaurants/{id} (and /menu-items/{id}): count 0 and average null while nothing is rated. */
export interface RatingDetails {
  id: number;
  average: number | null;
  count: number;
  /** How many gave 1 to 5 stars, by the number of stars */
  distribution: Record<'1' | '2' | '3' | '4' | '5', number>;
}

/** A customer's review as other customers see it: the name is shortened ("Priya S.") and nothing identifies them. */
export interface PublicReview {
  id: number;
  reviewerName: string;
  rating: number;
  comment: string | null;
  tags: ReviewTag[];
  dishes: DishRating[];
  /** ISO-8601 instants */
  createdAt: string;
  updatedAt: string;
}

export type PublicReviewSort = 'newest' | 'highest' | 'lowest';

/** What the stars of a list or card are made of: the ratings of a restaurant or of a dish. */
export type RatingLookup = Readonly<Record<number, RatingSummary | undefined>>;
