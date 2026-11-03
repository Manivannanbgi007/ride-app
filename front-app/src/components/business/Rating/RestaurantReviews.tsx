import { useState } from 'react';

import { RatingOverview } from '@/components/business/Rating/RatingOverview';
import { ReviewItem } from '@/components/business/Rating/ReviewItem';
import { AsyncContent } from '@/components/common/AsyncContent';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { Pagination } from '@/components/common/Pagination';
import { Select } from '@/components/common/Select';
import { useRestaurantRating, useRestaurantReviews } from '@/hooks/api/ratingHooks';
import type { PublicReviewSort } from '@/types/rating';
import { ERROR_MESSAGES } from '@/utils/errorMessages';

const PAGE_SIZE = 5;
const SORT_OPTIONS: readonly { value: PublicReviewSort; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'highest', label: 'Highest rated' },
  { value: 'lowest', label: 'Lowest rated' },
];

interface RestaurantReviewsProps {
  restaurantId: number;
  restaurantName: string;
  /** What the empty state says; the customers' wording by default (the owner area has its own) */
  emptyMessage?: string;
}

/**
 * "Ratings and reviews" at the end of a restaurant's page: the average and how the stars are spread, then what
 * customers wrote. Customers write reviews from their delivered orders, so the empty state says so.
 */
export function RestaurantReviews({
  restaurantId,
  restaurantName,
  emptyMessage,
}: RestaurantReviewsProps) {
  const rating = useRestaurantRating(restaurantId);

  return (
    <section
      id="reviews"
      aria-labelledby="restaurant-reviews"
      className="flex scroll-mt-28 flex-col gap-6 rounded-3xl bg-surface p-6 shadow-card sm:p-8"
    >
      <h2 id="restaurant-reviews" className="text-2xl font-extrabold tracking-tight">
        Ratings and reviews
      </h2>
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
              title="No ratings yet"
              message={
                emptyMessage ??
                `Order from ${restaurantName} and rate your order once it is delivered: yours can be the first.`
              }
            />
          ) : (
            <>
              <RatingOverview details={rating.data} />
              <ReviewList restaurantId={restaurantId} />
            </>
          ))}
      </AsyncContent>
    </section>
  );
}

function ReviewList({ restaurantId }: { restaurantId: number }) {
  const [sort, setSort] = useState<PublicReviewSort>('newest');
  const [page, setPage] = useState(0);
  const reviews = useRestaurantReviews(restaurantId, sort, page, PAGE_SIZE);

  return (
    <div className="flex flex-col gap-4 border-t border-line pt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="font-semibold">What customers say</h3>
        <Select
          label="Sort reviews"
          options={SORT_OPTIONS}
          value={sort}
          onChange={(event) => {
            setSort(event.target.value as PublicReviewSort);
            setPage(0);
          }}
        />
      </div>
      <AsyncContent
        isLoading={reviews.isPending}
        error={reviews.error}
        isEmpty={reviews.data?.content.length === 0}
        errorTitle={ERROR_MESSAGES.REVIEWS_LOAD_FAILED}
        onRetry={() => void reviews.refetch()}
        isRetrying={reviews.isFetching}
        loading={<SkeletonLoader lines={5} />}
        empty={<EmptyState title="No reviews on this page" />}
      >
        {reviews.data && (
          <div className="flex flex-col gap-4">
            <ul className={reviews.isPlaceholderData ? 'opacity-60' : undefined}>
              {reviews.data.content.map((review) => (
                <li key={review.id} className="border-b border-line py-4 first:pt-0 last:border-0">
                  <ReviewItem review={review} />
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
    </div>
  );
}
