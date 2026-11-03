import { Star } from 'lucide-react';

import { cn } from '@/utils/classNames';
import { formatAverage, ratingLabel, type ShownRating } from '@/utils/ratingUtils';

interface RatingBadgeProps {
  /** Null: no ratings yet. Undefined (not known yet): nothing is shown. */
  rating: ShownRating | null | undefined;
  className?: string;
}

/**
 * The rating on a card or list row: a star, the average and the number of reviews ("4.3 (12)"), or "No ratings
 * yet". The same look everywhere food and restaurants are listed.
 */
export function RatingBadge({ rating, className }: RatingBadgeProps) {
  if (rating === undefined) {
    return null;
  }
  const rated =
    rating !== null && rating.count > 0 && rating.average !== null
      ? { average: rating.average, count: rating.count }
      : null;
  return (
    <span
      role="img"
      aria-label={ratingLabel(rating)}
      className={cn(
        'inline-flex items-center gap-1 text-xs whitespace-nowrap',
        rated ? 'font-semibold text-ink' : 'text-ink-soft',
        className,
      )}
    >
      {rated ? (
        <>
          <Star aria-hidden="true" className="size-3.5 fill-warning-500 text-warning-500" />
          <span aria-hidden="true">
            {formatAverage(rated.average)}{' '}
            <span className="font-normal text-ink-soft">({rated.count})</span>
          </span>
        </>
      ) : (
        <span aria-hidden="true">No ratings yet</span>
      )}
    </span>
  );
}
