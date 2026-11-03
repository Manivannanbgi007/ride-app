import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { ReviewTagPill } from '@/components/business/Review/ReviewTagPill';
import { StarRating } from '@/components/common/StarRating';
import type { PublicReview } from '@/types/rating';
import { formatReviewDate } from '@/utils/reviewUtils';

/**
 * One review: the reviewer's initials, name, stars and day; what they wrote; the tags; each rated dish as a chip.
 * Shared by the restaurant's page (customers) and the owner's Reviews page.
 */
export function ReviewItem({ review }: { review: PublicReview }) {
  return (
    <article className="flex gap-4">
      <ReviewerAvatar name={review.reviewerName} className="shrink-0" />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h4 className="font-semibold">{review.reviewerName}</h4>
          <StarRating value={review.rating} size="sm" />
          <time dateTime={review.createdAt} className="text-sm text-ink-soft">
            {formatReviewDate(review.createdAt)}
          </time>
        </div>
        {review.comment && (
          <p className="text-sm leading-6 break-words text-ink">{review.comment}</p>
        )}
        {review.tags.length > 0 && (
          <ul aria-label="Tags" className="flex flex-wrap gap-2">
            {review.tags.map((tag) => (
              <li key={tag}>
                <ReviewTagPill tag={tag} />
              </li>
            ))}
          </ul>
        )}
        {review.dishes.length > 0 && (
          <ul aria-label="Dish ratings" className="flex flex-wrap gap-2 text-xs text-ink">
            {review.dishes.map((dish) => (
              <li
                key={dish.menuItemId}
                className="inline-flex items-center gap-2 rounded-full bg-page px-3 py-1 ring-1 ring-line"
              >
                {dish.dishName}
                <StarRating value={dish.rating} size="sm" />
              </li>
            ))}
          </ul>
        )}
      </div>
    </article>
  );
}
