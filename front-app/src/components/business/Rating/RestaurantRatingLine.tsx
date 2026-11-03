import { StarRating } from '@/components/common/StarRating';
import { useRestaurantRating } from '@/hooks/api/ratingHooks';
import { cn } from '@/utils/classNames';
import { formatAverage, formatReviewCount } from '@/utils/ratingUtils';

/**
 * The restaurant's average and number of reviews under its name; it leads to the reviews at the end of the page.
 * Nothing is shown while the rating loads or if it cannot be loaded (the reviews section explains that).
 * `onPhoto`: white text on a frosted pill, for the dark cover picture of the restaurant's page.
 */
export function RestaurantRatingLine({
  restaurantId,
  onPhoto = false,
}: {
  restaurantId: number;
  onPhoto?: boolean;
}) {
  const rating = useRestaurantRating(restaurantId);
  if (!rating.data) {
    return null;
  }
  if (rating.data.count === 0 || rating.data.average === null) {
    return (
      <p className={cn('text-sm', onPhoto ? 'text-white/90' : 'text-ink-soft')}>No ratings yet</p>
    );
  }
  return (
    <a
      href="#reviews"
      className={cn(
        'flex w-fit flex-wrap items-center gap-2 text-sm focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:outline-none',
        onPhoto
          ? 'rounded-full bg-white/15 px-3 py-1.5 ring-1 ring-white/25 backdrop-blur transition-colors hover:bg-white/25'
          : 'hover:underline',
      )}
    >
      <StarRating value={rating.data.average} size="sm" />
      <span className="font-semibold">{formatAverage(rating.data.average)}</span>
      <span className={onPhoto ? 'text-white/90' : 'text-ink-soft'}>
        ({formatReviewCount(rating.data.count)})
      </span>
    </a>
  );
}
