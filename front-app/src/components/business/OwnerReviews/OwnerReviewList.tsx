import { MessageSquareText, TriangleAlert } from 'lucide-react';
import { useState } from 'react';

import { ReviewItem } from '@/components/business/Rating/ReviewItem';
import { AsyncContent } from '@/components/common/AsyncContent';
import { Card } from '@/components/common/Card';
import { EmptyState } from '@/components/common/EmptyState';
import { SkeletonLoader } from '@/components/common/Loader';
import { Pagination } from '@/components/common/Pagination';
import { SegmentedControl } from '@/components/common/SegmentedControl';
import { useRestaurantReviews } from '@/hooks/api/ratingHooks';
import type { PublicReviewSort } from '@/types/rating';
import { cn } from '@/utils/classNames';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { stagger } from '@/utils/motion';

const PAGE_SIZE = 6;
const SORTS: readonly { id: PublicReviewSort; label: string }[] = [
  { id: 'newest', label: 'Newest' },
  { id: 'highest', label: 'Highest' },
  { id: 'lowest', label: 'Lowest' },
];
/** Reviews with this many stars or fewer are flagged: a customer was unhappy */
const LOW_RATING = 2;

interface OwnerReviewListProps {
  restaurantId: number;
  /** No review exists yet: the empty state is shown without asking for the list */
  hasReviews: boolean;
  className?: string;
}

/**
 * "What customers say" on the owner's Reviews page: every review, newest first (or the best or worst first), a few
 * at a time. Low ratings (one or two stars) are tinted and flagged, so the owner spots unhappy customers at once.
 */
export function OwnerReviewList({ restaurantId, hasReviews, className }: OwnerReviewListProps) {
  const [sort, setSort] = useState<PublicReviewSort>('newest');
  const [page, setPage] = useState(0);

  return (
    <Card
      title="What customers say"
      subtitle="Reviews of delivered orders, with the dishes they rated"
      className={className}
      actions={
        hasReviews && (
          <SegmentedControl
            label="Sort reviews"
            options={SORTS}
            value={sort}
            onChange={(next) => {
              setSort(next);
              setPage(0);
            }}
          />
        )
      }
    >
      {hasReviews ? (
        <ReviewPage restaurantId={restaurantId} sort={sort} page={page} onPageChange={setPage} />
      ) : (
        <EmptyState
          icon={MessageSquareText}
          title="No reviews yet"
          message="Customers can rate you once an order of theirs is delivered. Their ratings and comments appear here."
        />
      )}
    </Card>
  );
}

function ReviewPage({
  restaurantId,
  sort,
  page,
  onPageChange,
}: {
  restaurantId: number;
  sort: PublicReviewSort;
  page: number;
  onPageChange: (page: number) => void;
}) {
  const reviews = useRestaurantReviews(restaurantId, sort, page, PAGE_SIZE);

  return (
    <AsyncContent
      isLoading={reviews.isPending}
      error={reviews.data ? null : reviews.error}
      isEmpty={reviews.data?.content.length === 0}
      errorTitle={ERROR_MESSAGES.REVIEWS_LOAD_FAILED}
      onRetry={() => void reviews.refetch()}
      isRetrying={reviews.isFetching}
      loading={<SkeletonLoader lines={6} />}
      empty={<EmptyState title="No reviews on this page" />}
    >
      {reviews.data && (
        <div className="flex flex-col gap-5">
          <ul
            aria-label="Reviews"
            aria-busy={reviews.isPlaceholderData}
            className={cn(
              'flex flex-col gap-3 transition-opacity',
              reviews.isPlaceholderData && 'opacity-50',
            )}
          >
            {reviews.data.content.map((review, index) => {
              const low = review.rating <= LOW_RATING;
              return (
                <li
                  key={review.id}
                  style={stagger(index)}
                  className={cn(
                    'relative rounded-2xl p-4 ring-1 transition stagger motion-safe:animate-rise-in sm:p-5',
                    low
                      ? 'bg-st-rejected-soft/60 ring-st-rejected-text/25'
                      : 'bg-page/60 ring-line hover:ring-brand-200',
                  )}
                >
                  {low && (
                    <span className="mb-3 inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 text-xs font-semibold text-st-rejected-text ring-1 ring-st-rejected-text/20">
                      <TriangleAlert aria-hidden="true" className="size-3.5" />
                      Unhappy customer
                    </span>
                  )}
                  <ReviewItem review={review} />
                </li>
              );
            })}
          </ul>
          <Pagination
            page={reviews.data.page}
            size={reviews.data.size}
            totalPages={reviews.data.totalPages}
            totalElements={reviews.data.totalElements}
            summary={`Showing ${reviews.data.content.length} of ${reviews.data.totalElements} reviews`}
            onPageChange={onPageChange}
          />
        </div>
      )}
    </AsyncContent>
  );
}
