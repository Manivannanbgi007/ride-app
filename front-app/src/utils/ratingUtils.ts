import type { RatingLookup, RatingSummary } from '@/types/rating';

/** The rating shown on a card: stars made of at least one review, or "No ratings yet". */
export interface ShownRating {
  average: number | null;
  count: number;
}

/**
 * What to show for one restaurant or dish. Undefined while the ratings are not known (loading, or could not be
 * loaded): show nothing. Null once they are known and this one has no ratings yet.
 */
export function ratingOf(
  lookup: RatingLookup | undefined,
  id: number,
): RatingSummary | null | undefined {
  if (!lookup) {
    return undefined;
  }
  return lookup[id] ?? null;
}

/**
 * What to show for a search result, which carries its own rating (the same figures the sort and the filter use).
 * Undefined when the answer has none (an older search-service): no stars are shown.
 */
export function ratingOfHit(hit: {
  ratingAverage?: number | null;
  ratingCount?: number;
}): ShownRating | null | undefined {
  if (hit.ratingCount === undefined) {
    return undefined;
  }
  return hit.ratingCount > 0 && hit.ratingAverage !== null && hit.ratingAverage !== undefined
    ? { average: hit.ratingAverage, count: hit.ratingCount }
    : null;
}

/** 4 -> '4.0', 4.25 -> '4.3': always one decimal, so cards line up. */
export function formatAverage(average: number): string {
  return (Math.round(average * 10) / 10).toFixed(1);
}

/** '1 review', '12 reviews' (for dishes: ratings are counted the same way) */
export function formatReviewCount(count: number): string {
  return `${count} ${count === 1 ? 'review' : 'reviews'}`;
}

/** The text for screen readers: "Rated 4.3 out of 5, 12 reviews" or "No ratings yet". */
export function ratingLabel(rating: ShownRating | null): string {
  return rating && rating.count > 0 && rating.average !== null
    ? `Rated ${formatAverage(rating.average)} out of 5, ${formatReviewCount(rating.count)}`
    : 'No ratings yet';
}
