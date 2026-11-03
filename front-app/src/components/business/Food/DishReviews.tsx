import { ReviewerAvatar } from '@/components/business/Review/ReviewerAvatar';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Skeleton } from '@/components/common/Loader';
import { StarRating } from '@/components/common/StarRating';
import { useReviews, useReviewSummary } from '@/hooks/api/reviewHooks';
import type { Review } from '@/types/review';
import { formatRelativeTime } from '@/utils/dateUtils';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const PAGE_SIZE = 3;
const GRID = 'grid gap-6 sm:grid-cols-2 lg:grid-cols-3';

/** What customers said about this dish: the order's comment, and the stars they gave the dish itself. */
function DishReviewCard({ review, menuItemId }: { review: Review; menuItemId: number }) {
  const dishRating = review.dishRatings.find((dish) => dish.menuItemId === menuItemId)?.rating;
  return (
    <li className="flex flex-col gap-4 rounded-3xl bg-surface p-6 shadow-card">
      <div className="flex items-center gap-4">
        <ReviewerAvatar name={review.customerName} />
        <div className="min-w-0">
          <h3 className="truncate text-sm font-semibold">{review.customerName}</h3>
          <p className="text-xs text-ink-soft">{formatRelativeTime(review.createdAt)}</p>
        </div>
      </div>
      {review.comment ? (
        <p className="line-clamp-3 text-sm leading-6 break-words">{review.comment}</p>
      ) : (
        <p className="text-sm text-ink-soft italic">No comment</p>
      )}
      {dishRating !== undefined && <StarRating value={dishRating} size="lg" className="mt-auto" />}
    </li>
  );
}

function ReviewsLoader() {
  return (
    <ul role="status" aria-label="Loading" className={GRID}>
      {Array.from({ length: PAGE_SIZE }, (_, index) => (
        <li key={index} className="flex flex-col gap-4 rounded-3xl bg-surface p-6 shadow-card">
          <div className="flex items-center gap-4">
            <Skeleton className="size-11 rounded-full" />
            <Skeleton className="h-8 w-32" />
          </div>
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-5 w-28" />
        </li>
      ))}
    </ul>
  );
}

/** "Customer Reviews" of one dish: newest first, three at a time, with the dish's average and count. */
export function DishReviews({ menuItemId }: { menuItemId: number }) {
  const reviews = useReviews({ menuItemId, sort: 'createdAt,desc' }, PAGE_SIZE);
  const summary = useReviewSummary({ menuItemId });
  const rows = reviews.data?.pages.flatMap((page) => page.content) ?? [];
  const average = summary.data?.averageRating;

  return (
    <section aria-labelledby="dish-reviews-title" className="flex flex-col gap-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="dish-reviews-title" className="text-2xl font-bold tracking-tight">
          Customer Reviews
        </h2>
        {summary.data && summary.data.count > 0 && average !== null && average !== undefined && (
          <p className="text-sm text-ink-soft">
            <span className="font-semibold text-ink">{average.toFixed(1)}</span> ·{' '}
            {summary.data.count} {summary.data.count === 1 ? 'rating' : 'ratings'}
          </p>
        )}
      </div>

      <AsyncContent
        isLoading={reviews.isPending}
        error={reviews.data ? undefined : reviews.error}
        isEmpty={rows.length === 0}
        errorTitle={ERROR_MESSAGES.DISH_REVIEWS_LOAD_FAILED}
        onRetry={() => void reviews.refetch()}
        isRetrying={reviews.isFetching}
        loading={<ReviewsLoader />}
        empty={
          <div className="rounded-3xl bg-surface shadow-card">
            <EmptyState title="No reviews for this dish yet" />
          </div>
        }
      >
        <ul className={GRID}>
          {rows.map((review) => (
            <DishReviewCard key={review.id} review={review} menuItemId={menuItemId} />
          ))}
        </ul>
        {reviews.isFetchNextPageError && (
          <p role="alert" className="mt-4 text-center text-sm text-danger-text">
            Unable to load more reviews. Please try again.
          </p>
        )}
        {reviews.hasNextPage && (
          <div className="mt-6 flex justify-center">
            <Button
              variant="outline"
              onClick={() => void reviews.fetchNextPage()}
              isLoading={reviews.isFetchingNextPage}
            >
              Load more
            </Button>
          </div>
        )}
      </AsyncContent>
    </section>
  );
}
