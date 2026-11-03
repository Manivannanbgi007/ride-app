import { ChevronDown, MessageSquareText, SlidersHorizontal } from 'lucide-react';
import { useState } from 'react';

import { ReviewFilterBar } from '@/components/business/Review/ReviewFilterBar';
import { ReviewRow } from '@/components/business/Review/ReviewRow';
import { ReviewSummaryBar } from '@/components/business/Review/ReviewSummaryBar';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { ClearFiltersButton } from '@/components/common/ClearFiltersButton';
import { EmptyState } from '@/components/common/EmptyState';
import { ErrorMessage } from '@/components/common/ErrorMessage';
import { Skeleton } from '@/components/common/Loader';
import { Select } from '@/components/common/Select';
import { useReviews } from '@/hooks/api/reviewHooks';
import { useMenuDishLooks } from '@/hooks/api/storefrontHooks';
import type { ReviewFilters, ReviewScope, ReviewSort } from '@/types/review';
import { REVIEW_PAGE_SIZE, REVIEW_SORT_OPTIONS } from '@/utils/constants';
import { ERROR_MESSAGES } from '@/utils/errorMessages';
import { oneOf } from '@/utils/validationUtils';

const SORTS = REVIEW_SORT_OPTIONS.map((option) => option.value);

/**
 * "Customer reviews": the summary, the reviews of the period (and restaurant) with their sort and filters, and a
 * "Load More" button that adds the next page below.
 */
export function OthersReview({ scope }: { scope: ReviewScope }) {
  const [sort, setSort] = useState<ReviewSort>('createdAt,desc');
  const [narrow, setNarrow] = useState<Pick<ReviewFilters, 'tag' | 'rating'>>({});
  const [filtersOpen, setFiltersOpen] = useState(false);
  const filters: ReviewFilters = { ...scope, ...narrow, sort };
  const activeFilters = Object.values(narrow).filter((value) => value !== undefined).length;

  const reviews = useReviews(filters, REVIEW_PAGE_SIZE);
  const rows = reviews.data?.pages.flatMap((page) => page.content) ?? [];
  // The rated dishes' pictures, from the menus of the restaurants on the page
  const looks = useMenuDishLooks(rows.map((review) => review.restaurantId));

  return (
    <Card
      title="Customer reviews"
      subtitle="What customers say about the restaurants and their dishes"
      actions={
        <div className="flex items-center gap-3">
          <Button
            variant={filtersOpen || activeFilters > 0 ? 'secondary' : 'outline'}
            size="sm"
            aria-expanded={filtersOpen}
            aria-controls="review-filters"
            onClick={() => setFiltersOpen((open) => !open)}
            leftIcon={<SlidersHorizontal aria-hidden="true" className="size-4" />}
          >
            Filters{activeFilters > 0 && ` (${activeFilters})`}
          </Button>
          <div className="w-44">
            <Select
              aria-label="Sort reviews"
              options={REVIEW_SORT_OPTIONS}
              value={sort}
              onChange={(event) => setSort(oneOf(event.target.value, SORTS) ?? 'createdAt,desc')}
            />
          </div>
        </div>
      }
    >
      {filtersOpen && (
        <div id="review-filters" className="mb-4">
          <ReviewFilterBar
            filters={narrow}
            onChange={(changes) => setNarrow((current) => ({ ...current, ...changes }))}
            onClear={() => setNarrow({})}
          />
        </div>
      )}
      <ReviewSummaryBar scope={scope} />

      {reviews.isPending ? (
        <ReviewListSkeleton />
      ) : reviews.error && rows.length === 0 ? (
        <ErrorMessage
          title={ERROR_MESSAGES.REVIEWS_LOAD_FAILED}
          error={reviews.error}
          onRetry={() => void reviews.refetch()}
          isRetrying={reviews.isFetching}
        />
      ) : rows.length === 0 ? (
        <EmptyState
          icon={MessageSquareText}
          title={activeFilters > 0 ? 'No reviews match these filters' : 'No reviews yet'}
          message={
            activeFilters > 0
              ? undefined
              : 'Reviews show up here once customers rate their delivered orders.'
          }
          action={
            activeFilters > 0 && <ClearFiltersButton size="sm" onClick={() => setNarrow({})} />
          }
        />
      ) : (
        <>
          <ul className="divide-y divide-line">
            {rows.map((review) => (
              <ReviewRow key={review.id} review={review} looks={looks} />
            ))}
          </ul>
          {reviews.isFetchNextPageError && (
            <ErrorMessage
              title="Unable to load more reviews"
              error={reviews.error}
              onRetry={() => void reviews.fetchNextPage()}
              isRetrying={reviews.isFetchingNextPage}
            />
          )}
          {reviews.hasNextPage && !reviews.isFetchNextPageError && (
            <div className="flex justify-end border-t border-line pt-4">
              <Button
                onClick={() => void reviews.fetchNextPage()}
                isLoading={reviews.isFetchingNextPage}
              >
                Load More
                <ChevronDown aria-hidden="true" className="size-4" />
              </Button>
            </div>
          )}
        </>
      )}
    </Card>
  );
}

function ReviewListSkeleton() {
  return (
    <div role="status" aria-label="Loading reviews" className="flex flex-col gap-6">
      {[0, 1, 2].map((index) => (
        <div key={index} className="flex gap-4">
          <Skeleton className="size-11 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-2/3" />
          </div>
        </div>
      ))}
    </div>
  );
}
