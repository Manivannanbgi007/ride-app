import { MessageSquareText } from 'lucide-react';
import { useState } from 'react';

import { RatingOverview } from '@/components/business/Rating/RatingOverview';
import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { ReviewTagPill } from '@/components/business/Review/ReviewTagPill';
import { AsyncContent } from '@/components/common/AsyncContent';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { Modal } from '@/components/common/Modal';
import { Pagination } from '@/components/common/Pagination';
import { StarRating } from '@/components/common/StarRating';
import { useDishRating, useDishReviews } from '@/hooks/api/ratingHooks';
import type { PublicReview } from '@/types/rating';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { formatReviewDate } from '@/utils/reviewUtils';

const PAGE_SIZE = 5;

interface DishReviewsDialogProps {
  /** The dish whose reviews are shown; null keeps the dialog closed */
  dish: { id: number; name: string } | null;
  onClose: () => void;
  /** What the empty state says (customers are invited to rate; the owner area has its own words) */
  emptyMessage?: string;
}

/**
 * "Reviews of <dish>": the dish's average and how its stars are spread, then what customers wrote in the reviews
 * that rated it, newest first, a few at a time. Customers review a whole delivered order (one comment) and may give
 * each dish its own stars; each review here shows the stars this dish got, the comment and the tags, and the
 * order's overall stars. Names are shortened ("Priya S."): the same public reviews as on the restaurant's page.
 */
export function DishReviewsDialog({ dish, onClose, emptyMessage }: DishReviewsDialogProps) {
  return (
    <Modal
      open={dish !== null}
      onClose={onClose}
      title={dish ? `Reviews of ${dish.name}` : 'Reviews'}
      size="lg"
    >
      {dish && (
        // A new dish starts again at its first page
        <DishReviewsContent key={dish.id} menuItemId={dish.id} emptyMessage={emptyMessage} />
      )}
    </Modal>
  );
}

function DishReviewsContent({
  menuItemId,
  emptyMessage,
}: {
  menuItemId: number;
  emptyMessage?: string;
}) {
  const rating = useDishRating(menuItemId);

  return (
    // The reviews scroll inside the dialog; its title and close button stay in view
    <div className="-mx-6 -my-5 max-h-[70vh] overflow-y-auto px-6 py-5">
      <AsyncContent
        isLoading={rating.isPending}
        error={rating.error}
        errorTitle={ERROR_MESSAGES.RATINGS_LOAD_FAILED}
        onRetry={() => void rating.refetch()}
        isRetrying={rating.isFetching}
        loading={<SkeletonLoader lines={4} />}
      >
        {rating.data &&
          (rating.data.count === 0 ? (
            <EmptyState
              icon={MessageSquareText}
              title="No reviews for this dish yet"
              message={
                emptyMessage ??
                'Customers rate a dish when they review a delivered order with it: yours can be the first.'
              }
            />
          ) : (
            <div className="flex flex-col gap-6">
              <RatingOverview details={rating.data} />
              <DishReviewList menuItemId={menuItemId} />
            </div>
          ))}
      </AsyncContent>
    </div>
  );
}

function DishReviewList({ menuItemId }: { menuItemId: number }) {
  const [page, setPage] = useState(0);
  const reviews = useDishReviews(menuItemId, page, PAGE_SIZE);

  return (
    <section
      aria-labelledby={`dish-reviews-${menuItemId}`}
      className="flex flex-col gap-4 border-t border-line pt-6"
    >
      <h3 id={`dish-reviews-${menuItemId}`} className="font-semibold">
        What customers say
      </h3>
      <AsyncContent
        isLoading={reviews.isPending}
        error={reviews.error}
        isEmpty={reviews.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.DISH_REVIEWS_LOAD_FAILED}
        onRetry={() => void reviews.refetch()}
        isRetrying={reviews.isFetching}
        loading={<SkeletonLoader lines={5} />}
        empty={<EmptyState title="No reviews on this page" />}
      >
        {reviews.data && (
          <div className="flex flex-col gap-4">
            <ul
              aria-label="Reviews of this dish"
              className={
                reviews.isPlaceholderData ? 'flex flex-col gap-3 opacity-60' : 'flex flex-col gap-3'
              }
            >
              {reviews.data.content.map((review) => (
                <li key={review.id}>
                  <DishReviewItem review={review} menuItemId={menuItemId} />
                </li>
              ))}
            </ul>
            <Pagination
              page={reviews.data.page}
              size={reviews.data.size}
              totalPages={reviews.data.totalPages}
              totalElements={reviews.data.totalElements}
              summary={`Showing ${reviews.data.content.length} of ${reviews.data.totalElements} reviews`}
              onPageChange={setPage}
            />
          </div>
        )}
      </AsyncContent>
    </section>
  );
}

/** One review: who and when, the stars this dish got, what they wrote, the tags, and the order's overall stars. */
function DishReviewItem({ review, menuItemId }: { review: PublicReview; menuItemId: number }) {
  const dishStars = review.dishes.find((dish) => dish.menuItemId === menuItemId)?.rating;
  return (
    <article
      aria-label={`Review by ${review.reviewerName}`}
      className="flex flex-col gap-3 rounded-2xl bg-page p-4 ring-1 ring-line"
    >
      <div className="flex items-center gap-3">
        <ReviewerAvatar name={review.reviewerName} />
        <div className="min-w-0 flex-1">
          <h4 className="truncate font-semibold">{review.reviewerName}</h4>
          <time dateTime={review.createdAt} className="text-xs text-ink-soft">
            {formatReviewDate(review.createdAt)}
          </time>
        </div>
        {dishStars !== undefined && (
          <div className="flex shrink-0 flex-col items-end gap-0.5">
            <span className="text-xs text-ink-soft">This dish</span>
            <StarRating value={dishStars} size="sm" />
          </div>
        )}
      </div>
      {review.comment ? (
        <p className="text-sm leading-6 break-words whitespace-pre-line text-ink">
          {review.comment}
        </p>
      ) : (
        <p className="text-sm text-ink-soft italic">No comment written</p>
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
      <p className="flex items-center gap-2 text-xs text-ink-soft">
        The whole order:
        <StarRating value={review.rating} size="sm" />
      </p>
    </article>
  );
}
